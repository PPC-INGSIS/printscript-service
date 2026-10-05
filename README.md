# PrintScript

Servicio de **Snippet Searcher** encargado del lenguaje PrintScript. Valida, formatea, lintea y ejecuta código PrintScript usando la librería del TP1.

No guarda nada: no tiene base de datos ni estado. Tampoco sabe de usuarios ni de permisos. Lo llama el servicio de Snippets, nunca la UI.

## Stack

| | |
|---|---|
| Lenguaje | Kotlin 2.3 sobre Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Actuator) |
| Lenguaje PrintScript | `printscript:runner` 1.1.1 (librería del TP1) |
| Build | Gradle (Kotlin DSL) |
| Calidad | ktlint, detekt, JaCoCo |
| Tests | JUnit 5 |

## Requisitos

- Java 21
- Un token de GitHub con `read:packages` (ver [Credenciales de GitHub Packages](#credenciales-de-github-packages))

No necesita Docker.

## Correrlo localmente

```bash
./gradlew bootRun
```

El servicio queda escuchando en `http://localhost:8082`. Se corta con `Ctrl+C`.

Para verificar que está funcionando:

```bash
curl localhost:8082/actuator/health
# {"status":"UP"}  → todo bien
```

## Puertos

| Dónde corre | Escucha en | Se llega desde |
|---|---|---|
| Local (`bootRun`) | `8082` | `localhost:8082` |
| Docker (infraestructura) | `8080` (`SERVER_PORT=8080`) | Tu máquina: `127.0.0.1:8082`. Otro contenedor: `http://printscript:8080` |

El `8082` está reservado en el repo de infraestructura para no chocar con los otros servicios cuando corren todos en la misma máquina.

## Endpoints

### `POST /validate?version={id}`

Valida código que todavía no está guardado (crear o editar un snippet). Recibe el contenido, no una ruta.

```bash
curl -X POST "localhost:8082/validate?version=1.1" \
  -H "Content-Type: text/plain" --data-binary @snippet.ps
```

| | |
|---|---|
| `version` | Versión de PrintScript: `1.0` o `1.1` |
| `Content-Type` | `text/plain`, en UTF-8 |
| Body | El código fuente |

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, **también si el código es inválido** | `{"errors":[...]}` |
| `400` | La versión no existe | `{"message":"La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"}` |
| `400` | Falta `version` o el body está vacío | Error estándar de Spring |
| `415` | El `Content-Type` no es `text/plain` | Error estándar de Spring |

Lista vacía = el código es válido. Cada error trae dónde empieza:

```json
{"errors":[{"message":"Se esperaba un valor, un identificador o '('","line":1,"column":17}]}
```

`line` y `column` son **base 1**, tal como las da la librería: la primera letra del archivo es `1:1`.

## Credenciales de GitHub Packages

Las convenciones de build (`ppc.kotlin-service`) y la librería de PrintScript se bajan de GitHub Packages, que pide autenticación aunque el paquete sea público.

Creá un token (classic) con el permiso `read:packages` y agregalo en `~/.gradle/gradle.properties`:

```properties
gpr.user=tu-usuario-de-github
gpr.key=tu-token
```

- Ese archivo es de tu máquina y está fuera del repo: el token **nunca** se sube.
- En el CI no hace falta: usa `GITHUB_ACTOR` y `GITHUB_TOKEN`.

## Verificar

```bash
./gradlew check
```

Corre, en orden:

- **ktlint**: formato del código (espacios, saltos de línea, imports). Reglas en `.editorconfig`.
- **detekt**: estructura del código (complejidad, cantidad de returns, nombres). Reglas en `config/detekt/detekt.yml`.
- **Tests**.
- **Cobertura**: falla si queda por debajo del 80%. El reporte queda en `build/reports/jacoco/test/html/index.html`.

Si ktlint falla, la mayoría de los problemas se corrigen solos:

```bash
./gradlew ktlintFormat
```

## Git hooks

Se instalan solos la primera vez que corrés `./gradlew check`. Viven en `.githooks/` y se copian a `.git/hooks/`.

| Hook | Cuándo | Qué corre |
|---|---|---|
| `pre-commit` | Antes de cada commit | ktlint y detekt |
| `pre-push` | Antes de cada push | `./gradlew check` completo |

## CI

Cada pull request a `dev` o `main` corre `./gradlew check` en GitHub Actions, con el workflow reusable compartido por todos los servicios del proyecto (versión `v2`).
