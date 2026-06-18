# 1. Preparar o arquivo de entrada
# Colocar o CSV com URLs em: data/urls/ifood_urls.csv

# 2. Buildar e executar
docker-compose up --build

# 3. Executar em background
docker-compose up -d

# 4. Ver logs
docker-compose logs -f

# 5. Parar
docker-compose down

# 6. Limpar tudo (incluindo volumes)
docker-compose down -v

# Com Makefile

# Build
make build

# Executar
make run

# Ver logs
make logs

# Limpar
make clean