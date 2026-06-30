#!/bin/sh
set -e

echo "========================================="
echo "  iFood Crawler - Docker Entrypoint"
echo "========================================="
echo "Data: $(date)"
echo "Flaresolverr: ${CRAWLER_FLARESOLVERR_URL:-http://flaresolverr:8191/v1}"
echo "Arquivo de entrada: ${CRAWLER_INPUT_FILE:-/app/data/ifood_urls_padrao_item_1000.csv}"
echo "Arquivo de saida: ${CRAWLER_OUTPUT_FILE:-/app/output/results.json}"
echo "Paralelismo: ${CRAWLER_PARALLELISM:-5}"
echo "========================================="

if [ ! -f "${CRAWLER_INPUT_FILE:-/app/data/ifood_urls_padrao_item_1000.csv}" ]; then
    echo "ERRO: Arquivo de entrada nao encontrado em ${CRAWLER_INPUT_FILE}"
    echo "Certifique-se de que o arquivo CSV esta no diretorio ./data/"
    exit 1
fi

mkdir -p "$(dirname "${CRAWLER_OUTPUT_FILE:-/app/output/results.json}")"
mkdir -p "$(dirname "${CRAWLER_CHECKPOINT_DB_PATH:-/app/checkpoints/checkpoint.db}")"
mkdir -p "$(dirname "${CRAWLER_COOKIE_STORE_PATH:-/app/cookies/cookies.json}")"

echo "Aguardando Flaresolverr ficar disponivel..."
FLARESOLVERR_URL="${CRAWLER_FLARESOLVERR_URL:-http://flaresolverr:8191/v1}"
FLARESOLVERR_BASE="${FLARESOLVERR_URL%/v1}"
for i in $(seq 1 30); do
    if curl -s -o /dev/null "$FLARESOLVERR_BASE/health" 2>/dev/null; then
        echo "Flaresolverr esta pronto!"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "AVISO: Flaresolverr nao respondeu apos 30s. Continuando mesmo assim..."
    fi
    sleep 1
done

echo "Iniciando crawler..."
exec "$@"
