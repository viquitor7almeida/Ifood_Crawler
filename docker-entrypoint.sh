#!/bin/sh
set -e

# Script de entrada para o container
echo "========================================="
echo "  iFood Crawler - Docker Entrypoint"
echo "========================================="
echo "Data: $(date)"
echo "Arquivo de entrada: ${CRAWLER_INPUT_FILE:-/app/urls/ifood_urls.csv}"
echo "Arquivo de saída: ${CRAWLER_OUTPUT_FILE:-/app/output/results.json}"
echo "Paralelismo: ${CRAWLER_PARALLELISM:-5}"
echo "Max Retries: ${CRAWLER_MAX_RETRIES:-3}"
echo "========================================="

# Verificar se o arquivo de entrada existe
if [ ! -f "${CRAWLER_INPUT_FILE:-/app/urls/ifood_urls.csv}" ]; then
    echo "ERRO: Arquivo de entrada não encontrado!"
    echo "Por favor, coloque o arquivo CSV em: ${CRAWLER_INPUT_FILE:-/app/urls/ifood_urls.csv}"
    echo "Ou monte um volume com o arquivo:"
    echo "  docker run -v /caminho/para/urls.csv:/app/urls/ifood_urls.csv ..."
    exit 1
fi

# Criar diretório de saída se não existir
mkdir -p "$(dirname ${CRAWLER_OUTPUT_FILE:-/app/output/results.json})"

# Executar o crawler
echo "Iniciando crawler..."
exec "$@"