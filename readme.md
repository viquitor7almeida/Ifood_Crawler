# iFood Product Crawler

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)

[![Docker](https://img.shields.io/badge/Docker-24.0-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Solução de crawling distribuído para páginas de produtos do iFood, desenvolvida em Java 17 com arquitetura hexagonal, concorrência controlada e resiliência a falhas.**

---

## Índice

- [Visão Geral](#visão-geral)
- [Pré-requisitos](#pré-requisitos)
- [Instalação](#instalação)
- [Configuração](#configuração)
- [Execução](#execução)
- [Arquitetura](#arquitetura)
- [Estratégia de Crawler](#estratégia-de-crawler)
- [Tratamento de Erros](#tratamento-de-erros)
- [Métricas e Observabilidade](#métricas-e-observabilidade)
- [Evidências de Execução](#evidências-de-execução)
- [Limitações Conhecidas](#limitações-conhecidas)
- [Melhorias Futuras](#melhorias-futuras)
- [Licença](#licença)

---

## Visão Geral

O **iFood Product Crawler** é uma aplicação backend desenvolvida para coletar informações estruturadas de páginas de produtos do iFood a partir de uma lista de URLs fornecida em arquivo CSV.

### Características Principais

- **Processamento concorrente** de até 1.000 URLs com pool de workers configurável
- **Taxa de sucesso garantida ≥ 95%** com retry exponencial, rate limiting e checkpoint
- **Parser resiliente** com JSON-LD, data-testid, meta tags, __NEXT_DATA__ e fallback CSS
- **Proxy Cloudflare** via Flaresolverr com circuit breaker
- **Checkpoint automático** com SQLite para retomada de execução interrompida
- **Logging estruturado** com correlation ID para rastreabilidade
- **Métricas detalhadas** com percentis de latência e contadores
- **Containerização** com Docker e Docker Compose
- **Suíte de testes** unitários e de integração

### Tecnologias Utilizadas

| Tecnologia | Versão | Propósito |
|------------|--------|-----------|
| Java | 17 | Linguagem principal |
| Flaresolverr | latest | Bypass Cloudflare e renderização JS |
| JSoup | 1.17.2 | Parsing HTML fallback |
| SQLite | 3.44.1 | Checkpoint e persistência local |
| Jackson | 2.16.1 | Serialização JSON |
| Micrometer | 1.12.5 | Métricas e observabilidade |
| Logback | 1.4.14 | Logging estruturado |
| JUnit 5 | 5.10.1 | Testes unitários |
| Mockito | 5.7.0 | Mocks para testes |
| Docker | 24.0 | Containerização |

---

## Pré-requisitos

### Para execução local

- **Java 17** (JDK 17+)
- **Maven 3.9+**
- **Flaresolverr** rodando em http://localhost:8191
- **Arquivo CSV** com as URLs a serem processadas

### Para execução com Docker

- **Docker 24.0+**
- **Docker Compose 2.20+**

### Verificação de requisitos

```bash
# Verificar Java
java -version
# Deve mostrar: openjdk version "17.x.x"

# Verificar Maven
mvn -version
# Deve mostrar: Apache Maven 3.x.x

# Verificar Docker
docker --version
# Deve mostrar: Docker version 24.x.x