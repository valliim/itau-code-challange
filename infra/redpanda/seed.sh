#!/bin/bash
set -euo pipefail

BROKERS="${REDPANDA_BROKERS:-redpanda:9092}"
TOPIC_NAME="${ACCOUNTS_TOPIC:-conta-bancaria-criada}"

echo "Waiting for Redpanda broker at ${BROKERS}..."
until rpk cluster info --brokers "${BROKERS}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done
echo "Redpanda broker is ready."

if rpk topic describe "${TOPIC_NAME}" --brokers "${BROKERS}" >/dev/null 2>&1; then
  echo "Topic '${TOPIC_NAME}' already exists, skipping creation."
else
  echo "Creating topic '${TOPIC_NAME}'..."
  rpk topic create "${TOPIC_NAME}" --brokers "${BROKERS}" --partitions 1 --replicas 1
fi

echo "Seed complete. Topic '${TOPIC_NAME}' is ready."
