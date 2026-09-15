#!/bin/bash
set -euo pipefail

ENDPOINT_URL="${DYNAMODB_ENDPOINT_URL:-http://dynamodb:8000}"
ACCOUNTS_TABLE_NAME="${ACCOUNTS_TABLE_NAME:-Accounts}"
TRANSACTIONS_TABLE_NAME="${TRANSACTIONS_TABLE_NAME:-Transactions}"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"

echo "Waiting for DynamoDB Local at ${ENDPOINT_URL}..."
until aws dynamodb list-tables --endpoint-url "${ENDPOINT_URL}" --region "${REGION}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done
echo "DynamoDB Local is ready."

create_table_if_absent() {
  local table_name="$1"
  local key_attribute="$2"

  if aws dynamodb describe-table --table-name "${table_name}" --endpoint-url "${ENDPOINT_URL}" --region "${REGION}" >/dev/null 2>&1; then
    echo "Table '${table_name}' already exists, skipping creation."
  else
    echo "Creating table '${table_name}'..."
    aws dynamodb create-table \
      --table-name "${table_name}" \
      --attribute-definitions AttributeName="${key_attribute}",AttributeType=S \
      --key-schema AttributeName="${key_attribute}",KeyType=HASH \
      --billing-mode PAY_PER_REQUEST \
      --endpoint-url "${ENDPOINT_URL}" \
      --region "${REGION}" >/dev/null
    aws dynamodb wait table-exists \
      --table-name "${table_name}" \
      --endpoint-url "${ENDPOINT_URL}" \
      --region "${REGION}"
    echo "Table '${table_name}' created."
  fi
}

create_table_if_absent "${ACCOUNTS_TABLE_NAME}" "accountId"
create_table_if_absent "${TRANSACTIONS_TABLE_NAME}" "transactionId"

echo "Seed complete. '${ACCOUNTS_TABLE_NAME}' and '${TRANSACTIONS_TABLE_NAME}' are ready."
