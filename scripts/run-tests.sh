#!/bin/bash

set -e

echo "========================================="
echo "  iFood Crawler - Test Suite"
echo "========================================="

#cores
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

#executar testes unitários
echo -e "\n${YELLOW}Executando testes unitários...${NC}"
mvn test

if [ $? -eq 0 ]; then
    echo -e "${GREEN} Testes unitários passaram!${NC}"
else
    echo -e "${RED} Testes unitários falharam!${NC}"
    exit 1
fi

#executar testes de integração
echo -e "\n${YELLOW}Executando testes de integração...${NC}"
mvn verify

if [ $? -eq 0 ]; then
    echo -e "${GREEN} Testes de integração passaram!${NC}"
else
    echo -e "${RED} Testes de integração falharam!${NC}"
    exit 1
fi

#gerar relatorio de cobertura (se configurado)
echo -e "\n${YELLOW}Gerando relatório de cobertura...${NC}"
mvn jacoco:report

echo -e "\n${GREEN} Todos os testes concluídos com sucesso!${NC}"
echo -e "Relatório disponível em: target/site/jacoco/index.html"