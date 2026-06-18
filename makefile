#makefiles

.PHONY: help build run stop clean test shell logs

#variaveis
DOCKER_COMPOSE = docker-compose
PROJECT_NAME = ifood-crawler

help: #mostra ajuda
	@echo "Comandos disponíveis:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

build: #builda a imagem docker
	@echo "Buildando imagem..."
	$(DOCKER_COMPOSE) build

run: #executa o crawler (com build se necessário)
	@echo "Executando crawler..."
	$(DOCKER_COMPOSE) up --build

start: #inicia em background
	@echo "Iniciando em background..."
	$(DOCKER_COMPOSE) up -d

stop: #para o container
	@echo "Parando container..."
	$(DOCKER_COMPOSE) down

clean: #limpa containers, volumes e imagens
	@echo "Limpando..."
	$(DOCKER_COMPOSE) down -v
	@rm -rf data/output/* data/logs/* data/checkpoints/* 2>/dev/null || true
	@echo "Limpeza concluída!"

test: #executa testes
	@echo "Executando testes..."
	mvn test

shell: #abre um shell no container
	@echo "Abrindo shell..."
	$(DOCKER_COMPOSE) run --rm ifood-crawler /bin/sh

logs: #mostra logs do container
	@echo "Mostrando logs..."
	$(DOCKER_COMPOSE) logs -f

status: #mostra status do container
	@echo "Status:"
	$(DOCKER_COMPOSE) ps

checkpoint: #mostra estatísticas do checkpoint
	@echo "Estatísticas do checkpoint:"
	@sqlite3 data/checkpoints/checkpoint.db \
		"SELECT status, COUNT(*) FROM crawl_results GROUP BY status;" 2>/dev/null || \
		echo "Checkpoint não encontrado ou SQLite não instalado"

export: #exporta resultados em JSON e CSV
	@echo "Exportando resultados..."
	@mkdir -p data/output
	@if [ -f "data/checkpoints/checkpoint.db" ]; then \
		sqlite3 -csv -header data/checkpoints/checkpoint.db \
			"SELECT * FROM crawl_results;" > data/output/results.csv 2>/dev/null && \
		echo "CSV exportado para data/output/results.csv"; \
	else \
		echo "Checkpoint não encontrado"; \
	fi

#default target
default: help