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

### `POST /execute`

Ejecuta código con los inputs dados y devuelve lo que imprimió. Igual que `/validate`, recibe el contenido y no una ruta: leer el snippet del store es responsabilidad de Snippets. No compara contra valores esperados: eso también lo hace Snippets.

```bash
curl -X POST localhost:8082/execute \
  -H "Content-Type: application/json" \
  -d '{"code":"let nombre: string = readInput(\"Nombre: \");\nprintln(\"Hola \" + nombre);","version":"1.1","inputs":["Juan"]}'
```

| Campo | |
|---|---|
| `code` | El código fuente |
| `version` | Versión de PrintScript: `1.0` o `1.1` |
| `inputs` | Lo que devuelve cada `readInput`, en orden. `[]` si no lee nada. Los que sobran se ignoran |

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, **también si la ejecución falla** | `{"outputs":[...],"error":...}` |
| `400` | La versión no existe | `{"message":"La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"}` |
| `400` | Falta un campo del body | Error estándar de Spring |

Si terminó bien, `error` es `null`:

```json
{"outputs":["Nombre: ","Hola Juan"],"error":null}
```

Un error de sintaxis, uno de ejecución o la falta de inputs **no** son errores del pedido: responden `200` con el error y lo impreso hasta ese momento.

```json
{"outputs":["antes"],"error":{"message":"...","line":2,"column":9}}
```

A tener en cuenta:

- **El prompt de `readInput` sale como output.** La librería lo imprime antes de leer, así que `readInput("Nombre: ")` agrega `"Nombre: "` a `outputs`.
- **`readEnv` nunca lee las variables del servidor.** Cualquier `readEnv` corta la ejecución con `La variable de entorno 'X' no está definida.` y su posición.
- **Si faltan inputs, el error viene sin posición.** `line` y `column` llegan en `null`: la librería no le pasa la posición a quien da los inputs, y cuando el error llega al servicio ya no hay forma de saber en qué sentencia estaba. El mensaje dice cuántos se enviaron: `Faltan inputs: el snippet pidió más de los que se enviaron (se envió 1)`.

### `POST /format`

Formatea código con las reglas dadas y devuelve el resultado. Igual que `/execute`, recibe el contenido y no una ruta.

```bash
curl -X POST localhost:8082/format \
  -H "Content-Type: application/json" \
  -d '{"code":"let x:number=1;","version":"1.1","rules":{"enforce-spacing-around-equals":true}}'
```

| Campo | |
|---|---|
| `code` | El código fuente |
| `version` | Versión de PrintScript: `1.0` o `1.1` |
| `rules` | Las reglas de formato, con las mismas claves que el archivo de configuración de la librería. `{}` aplica los defaults |

**Reglas**

| Clave | Tipo | Valores |
|---|---|---|
| `enforce-spacing-before-colon-in-declaration` | booleano | `true`: un espacio antes del `:`. `false`: ninguno |
| `enforce-spacing-after-colon-in-declaration` | booleano | `true`: un espacio después del `:`. `false`: ninguno |
| `enforce-spacing-around-equals` | booleano | `true`: un espacio a cada lado del `=`. `false`: ninguno |
| `enforce-no-spacing-around-equals` | booleano | `true`: ningún espacio alrededor del `=`. `false`: uno |
| `line-breaks-after-println` | entero | Líneas en blanco después de un `println`, de `0` a `2` |
| `mandatory-line-break-after-statement` | booleano | Un salto de línea después de cada `;` |
| `mandatory-space-surrounding-operations` | booleano | Un espacio a cada lado de los operadores |
| `mandatory-single-space-separation` | booleano | Un solo espacio entre tokens |
| `if-brace-same-line` | booleano | La `{` del `if` en la misma línea |
| `if-brace-below-line` | booleano | La `{` del `if` en la línea de abajo |
| `indent-inside-if` | entero | Espacios de indentación dentro de un `if`, de `0` a `8` |

Una regla que no aparece no se aplica: esa parte del código queda como estaba. Si dos reglas tocan lo mismo (por ejemplo, las dos de `=`), gana la última.

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, **también si el código tiene un error** | `{"formatted":...,"error":...}` |
| `400` | La versión no existe | `{"message":"La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"}` |
| `400` | Las reglas son inválidas | `{"message":"Unknown formatting rule: 'no-existe'"}` |
| `400` | Falta un campo del body | Error estándar de Spring |

Si terminó bien, `error` es `null`:

```json
{"formatted":"let x:number = 1;","error":null}
```

Un error en el código **no** es un error del pedido: responde `200` con el error y `formatted` en `null`.

```json
{"formatted":null,"error":{"message":"...","line":2,"column":19}}
```

Las reglas inválidas responden con el mensaje de la librería, que puede ser:

- Una clave que no existe: `Unknown formatting rule: 'no-existe'`
- Un booleano donde va un entero, o al revés: `Rule 'indent-inside-if' expects a int value`
- Un entero fuera de rango: `Rule 'indent-inside-if' expects a value within 0..8, got 9`
- Un valor que no es booleano ni entero: `La regla 'indent-inside-if' tiene que ser un booleano o un numero entero`

A tener en cuenta:

- **Solo detecta errores léxicos.** El formateador trabaja sobre tokens, sin parsear: un carácter inválido o un string sin cerrar dan error, pero `let x: number = ;` se formatea igual. Para saber si el código es válido está `/validate`.
- **Se descarta el whitespace del final.** Los espacios y saltos de línea después del último token no salen en `formatted`.

### `POST /lint`

Analiza código con las reglas dadas y devuelve las violaciones. Igual que `/format`, recibe el contenido y no una ruta.

```bash
curl -X POST localhost:8082/lint \
  -H "Content-Type: application/json" \
  -d '{"code":"let my_var: number = 1;","version":"1.1","rules":{"identifier_format":"camel case"}}'
```

| Campo | |
|---|---|
| `code` | El código fuente |
| `version` | Versión de PrintScript: `1.0` o `1.1` |
| `rules` | Las reglas de lint, con las mismas claves que el archivo de configuración de la librería. `{}` aplica los defaults |

**Reglas**

| Clave | Tipo | Valores |
|---|---|---|
| `identifier_format` | string | `"camel case"` o `"snake case"`: la convención que tienen que seguir los nombres de las variables |
| `mandatory-variable-or-literal-in-println` | booleano | `true`: el argumento de `println` tiene que ser un identificador o un literal, no una expresión |
| `mandatory-variable-or-literal-in-readInput` | booleano | `true`: lo mismo para `readInput`. Solo aplica en `1.1` |

Una regla que no aparece no se aplica: con `{}` no hay violaciones posibles, salvo los errores de sintaxis.

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, haya o no violaciones | `{"violations":[...]}` |
| `400` | La versión no existe | `{"message":"La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"}` |
| `400` | Las reglas son inválidas | `{"message":"Regla de analisis desconocida: 'no-existe'"}` |
| `400` | Falta un campo del body | Error estándar de Spring |

Lista vacía = sin violaciones. Cada violación trae la regla, dónde empieza y su severidad:

```json
{"violations":[{"rule":"identifier-naming","message":"El identificador 'my_var' no sigue la convención de nombres configurada.","line":1,"column":5,"severity":"WARNING"}]}
```

| `rule` | Regla que la genera | `severity` |
|---|---|---|
| `identifier-naming` | `identifier_format` | `WARNING` |
| `println-argument` | `mandatory-variable-or-literal-in-println` | `ERROR` |
| `read-input-argument` | `mandatory-variable-or-literal-in-readInput` | `ERROR` |
| `syntax` | Ninguna: es un error de sintaxis | `ERROR` |

`line` y `column` son **base 1**, como en `/validate`.

Las reglas inválidas responden con el mensaje de la librería, que puede ser:

- Una clave que no existe: `Regla de analisis desconocida: 'no-existe'`
- Una convención que no existe: `'identifier_format' tiene que ser 'camel case' o 'snake case'`
- Un valor que no es booleano: `'mandatory-variable-or-literal-in-println' tiene que ser true o false`

A tener en cuenta:

- **Un error de sintaxis no corta el análisis.** Aparece en `violations` con `rule` `syntax`, y la librería sigue con la sentencia siguiente: las violaciones de las demás sentencias salen igual.

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
