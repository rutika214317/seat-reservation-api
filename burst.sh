#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-}"
if [[ -z "$BASE_URL" ]]; then
	echo "Usage: bash burst.sh <BASE_URL>" >&2
	exit 2
fi
BASE_URL="${BASE_URL%/}"

ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-change-this-local-password}"
BURST_USERS="${BURST_USERS:-buyer01:local-buyer-01,buyer02:local-buyer-02,buyer03:local-buyer-03,buyer04:local-buyer-04,buyer05:local-buyer-05,buyer06:local-buyer-06,buyer07:local-buyer-07,buyer08:local-buyer-08}"
LIMIT_USER="${LIMIT_USER:-limit-user:local-limit-pass}"
RETRY_USER="${RETRY_USER:-retry-user:local-retry-pass}"
BURST_REQUESTS="${BURST_REQUESTS:-20000}"
BURST_WORKERS="${BURST_WORKERS:-128}"

IFS=',' read -r -a users <<< "$BURST_USERS"
if (( ${#users[@]} == 0 )); then
	echo "BURST_USERS must include at least one configured username:password pair" >&2
	exit 2
fi
if ! [[ "$BURST_REQUESTS" =~ ^[1-9][0-9]*$ && "$BURST_WORKERS" =~ ^[1-9][0-9]*$ ]]; then
	echo "BURST_REQUESTS and BURST_WORKERS must be positive integers" >&2
	exit 2
fi
if (( BURST_WORKERS > 1024 )); then
	echo "BURST_WORKERS must not exceed 1024" >&2
	exit 2
fi

for credential in "${users[@]}" "$LIMIT_USER" "$RETRY_USER"; do
	if [[ "$credential" != *:* || -z "${credential%%:*}" || -z "${credential#*:}" ]]; then
		echo "Every configured test identity must be written as username:password" >&2
		exit 2
	fi
done

work_dir="$(mktemp -d)"
hot_results="$work_dir/hot.tsv"
limit_results="$work_dir/limit.tsv"
trap 'rm -f "$hot_results" "$limit_results"; rmdir "$work_dir"' EXIT
touch "$hot_results" "$limit_results"

seat_list='["HOT-1"'
for ((seat = 1; seat <= 10; seat++)); do
	seat_list+=",\"LIMIT-$seat\""
done
seat_list+=',"RETRY-1"]'

show_response="$(curl --silent --show-error --fail-with-body \
	--user "$ADMIN_USERNAME:$ADMIN_PASSWORD" \
	--header 'Content-Type: application/json' \
	--data "{\"name\":\"burst-$(date +%s)-$$\",\"seats\":$seat_list,\"price_paise\":25000}" \
	"$BASE_URL/shows")"
show_id="$(printf '%s' "$show_response" | grep -oE '"id"[[:space:]]*:[[:space:]]*"[0-9a-fA-F-]+"' | head -n 1 | sed -E 's/.*"([0-9a-fA-F-]+)"/\1/')"
if [[ -z "$show_id" ]]; then
	echo "Could not read show id from create response: $show_response" >&2
	exit 1
fi

export BASE_URL show_id hot_results limit_results BURST_USERS LIMIT_USER BURST_WORKERS
export ADMIN_USERNAME ADMIN_PASSWORD

echo "Created show $show_id"
echo "Hot-seat storm: $BURST_REQUESTS requests, $BURST_WORKERS workers, ${#users[@]} buyer accounts"

seq 1 "$BURST_REQUESTS" | xargs -n 1 -P "$BURST_WORKERS" bash -c '
	index="$1"
	IFS="," read -r -a credentials <<< "$BURST_USERS"
	position=$(( (index - 1) % ${#credentials[@]} ))
	credential="${credentials[$position]}"
	username="${credential%%:*}"
	password="${credential#*:}"
	body_file="$(mktemp)"
	status="$(curl --silent --show-error --max-time 60 \
		--user "$username:$password" \
		--header "Content-Type: application/json" \
		--header "Idempotency-Key: hot-$show_id-$index" \
		--data "{\"seats\":[\"HOT-1\"]}" \
		--output "$body_file" --write-out "%{http_code}" \
		"$BASE_URL/shows/$show_id/reserve" 2>/dev/null || true)"
	body="$(cat "$body_file")"
	rm -f "$body_file"
	reason="other"
	if [[ "$status" == "409" ]]; then
		if grep -qi "per-user seat limit exceeded" <<< "$body"; then
			reason="per-user-limit"
		else
			reason="seat-taken"
		fi
	fi
	printf "%s\t%s\t%s\n" "$status" "$reason" "$username" >> "$hot_results"
' _

hot_confirmed="$(awk -F '\t' '$1 == "201" { count++ } END { print count + 0 }' "$hot_results")"
hot_taken="$(awk -F '\t' '$2 == "seat-taken" { count++ } END { print count + 0 }' "$hot_results")"
hot_limit="$(awk -F '\t' '$2 == "per-user-limit" { count++ } END { print count + 0 }' "$hot_results")"
hot_5xx="$(awk -F '\t' '$1 ~ /^5[0-9][0-9]$/ { count++ } END { print count + 0 }' "$hot_results")"
hot_transport="$(awk -F '\t' '$1 == "000" { count++ } END { print count + 0 }' "$hot_results")"
hot_other="$(awk -F '\t' '$1 !~ /^(201|409|000)$/ { count++ } END { print count + 0 }' "$hot_results")"
printf 'Hot-seat outcomes: confirmed=%s declined-seat-taken=%s declined-per-user-limit=%s 5xx=%s transport-errors=%s other=%s\n' \
	"$hot_confirmed" "$hot_taken" "$hot_limit" "$hot_5xx" "$hot_transport" "$hot_other"

printf '%s\n' {1..10} | xargs -n 1 -P 10 bash -c '
	seat="$1"
	body_file="$(mktemp)"
	status="$(curl --silent --show-error --max-time 60 \
		--user "${LIMIT_USER%%:*}:${LIMIT_USER#*:}" \
		--header "Content-Type: application/json" \
		--header "Idempotency-Key: limit-$show_id-$seat" \
		--data "{\"seats\":[\"LIMIT-$seat\"]}" \
		--output "$body_file" --write-out "%{http_code}" \
		"$BASE_URL/shows/$show_id/reserve" 2>/dev/null || true)"
	body="$(cat "$body_file")"
	rm -f "$body_file"
	reason="other"
	if [[ "$status" == "409" ]]; then
		if grep -qi "per-user seat limit exceeded" <<< "$body"; then
			reason="per-user-limit"
		else
			reason="seat-taken"
		fi
	fi
	printf "%s\t%s\t%s\n" "$status" "$reason" "${LIMIT_USER%%:*}" >> "$limit_results"
' _

limit_confirmed="$(awk -F '\t' '$1 == "201" { count++ } END { print count + 0 }' "$limit_results")"
limit_declined="$(awk -F '\t' '$2 == "per-user-limit" { count++ } END { print count + 0 }' "$limit_results")"
limit_5xx="$(awk -F '\t' '$1 ~ /^5[0-9][0-9]$/ { count++ } END { print count + 0 }' "$limit_results")"
limit_transport="$(awk -F '\t' '$1 == "000" { count++ } END { print count + 0 }' "$limit_results")"
limit_other="$(awk -F '\t' '$1 !~ /^(201|409|000)$/ { count++ } END { print count + 0 }' "$limit_results")"
printf 'Per-user limit outcomes: confirmed=%s declined-by-limit=%s 5xx=%s transport-errors=%s other=%s\n' \
	"$limit_confirmed" "$limit_declined" "$limit_5xx" "$limit_transport" "$limit_other"

retry_username="${RETRY_USER%%:*}"
retry_password="${RETRY_USER#*:}"
retry_key="retry-$show_id"
retry_first="$(curl --silent --show-error --fail-with-body \
	--user "$retry_username:$retry_password" \
	--header 'Content-Type: application/json' \
	--header "Idempotency-Key: $retry_key" \
	--data '{"seats":["RETRY-1"]}' "$BASE_URL/shows/$show_id/reserve")"
retry_again="$(curl --silent --show-error --fail-with-body \
	--user "$retry_username:$retry_password" \
	--header 'Content-Type: application/json' \
	--header "Idempotency-Key: $retry_key" \
	--data '{"seats":["RETRY-1"]}' "$BASE_URL/shows/$show_id/reserve")"
reservation_id() {
	printf '%s' "$1" | grep -oE '"reservation_id"[[:space:]]*:[[:space:]]*"[0-9a-fA-F-]+"' |
		head -n 1 | sed -E 's/.*"([0-9a-fA-F-]+)"/\1/'
}
if [[ -z "$(reservation_id "$retry_first")" || "$(reservation_id "$retry_first")" != "$(reservation_id "$retry_again")" ]]; then
	echo "Idempotency replay did not return the original reservation" >&2
	exit 1
fi
echo "Idempotent retry: two 201 responses returned the same reservation id"

state="$(curl --silent --show-error --fail-with-body "$BASE_URL/shows/$show_id")"
read_json_count() {
	printf '%s' "$state" | sed -nE "s/.*\"$1\"[[:space:]]*:[[:space:]]*([0-9]+).*/\\1/p" | head -n 1
}
total="$(read_json_count total_seats)"
available="$(read_json_count available_seats)"
held="$(read_json_count held_seats)"
confirmed="$(read_json_count confirmed_seats)"
if [[ ! "$total" =~ ^[0-9]+$ || ! "$available" =~ ^[0-9]+$ || ! "$held" =~ ^[0-9]+$ || ! "$confirmed" =~ ^[0-9]+$ ]]; then
	echo "Could not read seat counts from final show state: $state" >&2
	exit 1
fi
printf 'Final reconciliation: total=%s available=%s held=%s confirmed=%s sum=%s\n' \
	"$total" "$available" "$held" "$confirmed" "$((available + held + confirmed))"

if (( hot_confirmed != 1 || hot_taken != BURST_REQUESTS - 1 ||
	hot_limit != 0 || hot_5xx != 0 || hot_transport != 0 || hot_other != 0 )); then
	echo "Hot-seat burst did not produce exactly one winner and clean 409 declines" >&2
	exit 1
fi
if (( limit_confirmed > 4 || limit_5xx != 0 || limit_transport != 0 || limit_other != 0 )); then
	echo "Per-user limit burst violated the four-seat limit or returned an unexpected response" >&2
	exit 1
fi
if (( total != available + held + confirmed )); then
	echo "Show seat counts do not reconcile" >&2
	exit 1
fi
echo "Burst checks passed. Show id: $show_id"
