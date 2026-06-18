#!/bin/bash
#script helper para executar o crawler com docker

set -e

#cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' #no Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  iFood Crawler - Docker Runner${NC}"
echo -e "${GREEN}========================================${NC}"

#verificar se o Docker esta instalado
if ! command -v docker &> /dev/null; then
    echo -e "${RED}ERRO: Docker não encontrado. Instale o Docker primeiro.${NC}"
    exit 1
fi

#verificar se o Docker Compose esta disponível
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}ERRO: Docker Compose não encontrado.${NC}"
    exit 1
fi

#criar diretorios necessarios
echo -e "${YELLOW}Criando diretórios...${NC}"
mkdir -p data/urls data/output data/checkpoints data/logs

#verificar se o arquivo de URLs existe
if [ ! -f "data/urls/ifood_urls.csv" ]; then
    echo -e "${YELLOW}ATENÇÃO: Arquivo de URLs não encontrado em data/urls/ifood_urls.csv${NC}"
    echo -e "${YELLOW}Por favor, coloque o arquivo CSV com as URLs em: data/urls/ifood_urls.csv${NC}"
    echo -e "${YELLOW}Exemplo de formato:${NC}"
    echo -e "  https://www.ifood.com.br/produto/1"
    echo -e "  https://www.ifood.com.br/produto/2"
    echo -e "${YELLOW}Deseja continuar? (s/N)${NC}"
    read -r response
    if [[ ! "$response" =~ ^[Ss]$ ]]; then
        exit 0
    fi
fi

#escolher modo de execuçao
echo -e "${YELLOW}Escolha o modo de execução:${NC}"
echo "1) Build + Run (com Docker Compose)"
echo "2) Run apenas (usar imagem existente)"
echo "3) Build apenas"
echo "4) Remover containers e limpar"
read -r mode

case $mode in
    1)
        echo -e "${GREEN}Build e Run com Docker Compose...${NC}"
        docker-compose up --build
        ;;
    2)
        echo -e "${GREEN}Executando com Docker Compose...${NC}"
        docker-compose up
        ;;
    3)
        echo -e "${GREEN}Buildando imagem...${NC}"
        docker-compose build
        ;;
    4)
        echo -e "${YELLOW}Removendo containers e limpando...${NC}"
        docker-compose down -v
        docker rmi ifood-crawler 2>/dev/null || true
        echo -e "${GREEN}Limpeza concluída!${NC}"
        ;;
    *)
        echo -e "${RED}Opção inválida!${NC}"
        exit 1
        ;;
esac

#mostrar resultados
if [ -f "data/output/results.json" ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Crawler finalizado com sucesso!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo -e "Resultados disponíveis em: ${YELLOW}data/output/results.json${NC}"
    
    #mostrar resumo
    echo -e "\n${YELLOW}Resumo da execução:${NC}"
    if command -v jq &> /dev/null; then
        TOTAL=$(jq '. | length' data/output/results.json 2>/dev/null || echo "?")
        SUCCESS=$(jq '[.[] | select(.productData.status == "SUCCESS")] | length' data/output/results.json 2>/dev/null || echo "?")
        ERROR=$(jq '[.[] | select(.productData.status == "ERROR")] | length' data/output/results.json 2>/dev/null || echo "?")
        echo -e "  Total: ${TOTAL}"
        echo -e "  Sucessos: ${GREEN}${SUCCESS}${NC}"
        echo -e "  Falhas: ${RED}${ERROR}${NC}"
    else
        echo -e "  Instale 'jq' para ver o resumo formatado"
        echo -e "  Arquivo: data/output/results.json"
    fi
fi

echo -e "\n${GREEN}Logs disponíveis em: data/logs/${NC}"