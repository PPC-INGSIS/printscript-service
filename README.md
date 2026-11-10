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

## Errores

Los errores del pedido (4xx) responden con Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) y `Content-Type: application/problem+json`. **Los clientes tienen que leer `detail`.**

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1",
  "instance": "/format",
  "message": "La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"
}
```

- **`message` está deprecado y se va a quitar.** Trae el mismo texto que `detail`.
- **Los 4xx que arma Spring no traen `message`:** falta un campo o un parámetro, JSON mal formado, `415`.
- **`type` no viene en el body.** Según el RFC, si falta equivale a `about:blank`.

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
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |
| `400` | Falta `version` o el body está vacío | Problem Details de Spring |
| `415` | El `Content-Type` no es `text/plain` | Problem Details de Spring |

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
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |
| `400` | Falta un campo del body | Problem Details de Spring |

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
  -d '{"code":"let x:number=1;","version":"1.1","rules":{"space-around-assignment":"always"}}'
```

| Campo | |
|---|---|
| `code` | El código fuente |
| `version` | Versión de PrintScript: `1.0` o `1.1` |
| `rules` | Las reglas de formato, por id. `{}` aplica los defaults |

**Reglas**

| Id | Valor | Desde | Qué hace |
|---|---|---|---|
| `space-before-colon` | `"always"` o `"never"` | `1.0` | Un espacio antes del `:` de la declaración, o ninguno |
| `space-after-colon` | `"always"` o `"never"` | `1.0` | Un espacio después del `:` de la declaración, o ninguno |
| `space-around-assignment` | `"always"` o `"never"` | `1.0` | Un espacio a cada lado del `=`, o ninguno |
| `blank-lines-after-println` | entero, de `0` a `2` | `1.0` | Líneas en blanco después de un `println` |
| `line-break-after-statement` | `true` | `1.0` | Un salto de línea después de cada `;` |
| `space-around-operators` | `true` | `1.0` | Un espacio a cada lado de los operadores |
| `single-space-separation` | `true` | `1.0` | Un solo espacio entre tokens |
| `if-brace-position` | `"same-line"` o `"next-line"` | `1.1` | La `{` del `if` en la misma línea o en la de abajo |
| `if-indent` | entero, de `0` a `8` | `1.1` | Espacios de indentación dentro de un `if` |

- **Una regla que no aparece no se aplica:** esa parte del código queda como estaba.
- **Las reglas de valor `true` son flags:** `true` las activa y `false` equivale a no mandarlas.
- **Las reglas de una versión posterior a la del snippet se ignoran:** `if-indent` en un snippet `1.0` no es un error, no se aplica y su id vuelve en `ignoredRules`. El valor se valida igual.

**Claves deprecadas**

Las claves del archivo de configuración de la librería, que `/format` aceptaba antes, se siguen aceptando como alias. **Se van a quitar:** hay que pasar a los ids de arriba.

| Clave vieja | Con `true` | Con `false` |
|---|---|---|
| `enforce-spacing-before-colon-in-declaration` | `space-before-colon: "always"` | `space-before-colon: "never"` |
| `enforce-spacing-after-colon-in-declaration` | `space-after-colon: "always"` | `space-after-colon: "never"` |
| `enforce-spacing-around-equals` | `space-around-assignment: "always"` | `space-around-assignment: "never"` |
| `enforce-no-spacing-around-equals` | `space-around-assignment: "never"` | `space-around-assignment: "always"` |
| `mandatory-line-break-after-statement` | `line-break-after-statement: true` | ausente |
| `mandatory-space-surrounding-operations` | `space-around-operators: true` | ausente |
| `mandatory-single-space-separation` | `single-space-separation: true` | ausente |
| `if-brace-same-line` | `if-brace-position: "same-line"` | ausente |
| `if-brace-below-line` | `if-brace-position: "next-line"` | ausente |
| `line-breaks-after-println: n` | `blank-lines-after-println: n` | |
| `indent-inside-if: n` | `if-indent: n` | |

- **Si el pedido usa alguna, la respuesta `200` trae el header `Deprecation: @1793836800`** ([RFC 9745](https://www.rfc-editor.org/rfc/rfc9745)): deprecadas desde el 2026-11-05.
- **Dos claves que definen la misma regla con valores distintos son un `400`,** por ejemplo `enforce-spacing-around-equals` y `enforce-no-spacing-around-equals` en `true`. Antes ganaba la última. Con el mismo valor no hay error.

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, **también si el código tiene un error** | `{"formatted":...,"error":...,"ignoredRules":[...]}` |
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |
| `400` | Las reglas son inválidas | Problem Details con `rule` (ver abajo) |
| `400` | Falta un campo del body | Problem Details de Spring |

Si terminó bien, `error` es `null`. `ignoredRules` siempre viene, vacía si no se ignoró nada:

```json
{"formatted":"let x:number = 1;","error":null,"ignoredRules":[]}
```

Un error en el código **no** es un error del pedido: responde `200` con el error y `formatted` en `null`.

```json
{"formatted":null,"error":{"message":"...","line":2,"column":19},"ignoredRules":[]}
```

Las reglas inválidas traen en `rule` el id (o la clave vieja) que falló:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12",
  "instance": "/format",
  "message": "La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12",
  "rule": "if-indent"
}
```

- Un id que no existe: `Regla de formato desconocida: 'no-existe'`
- Un valor que no está entre los permitidos: `La regla 'space-before-colon' tiene que ser 'always' o 'never'`
- Algo que no es un entero donde va un entero: `La regla 'if-indent' espera un número entero`
- Un entero fuera de rango: `La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12`
- Algo que no es booleano en un flag: `La regla 'line-break-after-statement' solo acepta true`
- Algo que no es booleano en una clave vieja: `La regla 'if-brace-same-line' tiene que ser true o false`
- Dos claves con valores distintos para la misma regla: `'enforce-spacing-around-equals' y 'enforce-no-spacing-around-equals' definen 'space-around-assignment' con valores distintos`. Acá `rule` es el id nuevo.

A tener en cuenta:

- **Solo detecta errores léxicos.** El formateador trabaja sobre tokens, sin parsear: un carácter inválido o un string sin cerrar dan error, pero `let x: number = ;` se formatea igual. Para saber si el código es válido está `/validate`.
- **Se descarta el whitespace del final.** Los espacios y saltos de línea después del último token no salen en `formatted`.

### `POST /lint`

Analiza código con las reglas dadas y devuelve las violaciones. Igual que `/format`, recibe el contenido y no una ruta.

```bash
curl -X POST localhost:8082/lint \
  -H "Content-Type: application/json" \
  -d '{"code":"let my_var: number = 1;","version":"1.1","rules":{"identifier-naming":"camel-case"}}'
```

| Campo | |
|---|---|
| `code` | El código fuente |
| `version` | Versión de PrintScript: `1.0` o `1.1` |
| `rules` | Las reglas de lint, por id. `{}` aplica los defaults |

**Reglas**

| Id | Valor | Desde | Qué hace |
|---|---|---|---|
| `identifier-naming` | `"camel-case"` o `"snake-case"` | `1.0` | La convención que tienen que seguir los nombres de las variables |
| `println-argument` | `true` | `1.0` | El argumento de `println` tiene que ser un identificador o un literal, no una expresión |
| `read-input-argument` | `true` | `1.1` | Lo mismo para `readInput` |

Los ids son los mismos `rule` que traen las violaciones.

- **Una regla que no aparece no se aplica:** con `{}` no hay violaciones posibles, salvo los errores de sintaxis.
- **Las reglas de valor `true` son flags:** `true` las activa y `false` equivale a no mandarlas.
- **Las reglas de una versión posterior a la del snippet se ignoran:** `read-input-argument` en un snippet `1.0` no es un error, no se aplica y su id vuelve en `ignoredRules`. El valor se valida igual.

**Claves deprecadas**

Las claves del archivo de configuración de la librería, que `/lint` aceptaba antes, se siguen aceptando como alias. **Se van a quitar:** hay que pasar a los ids de arriba.

| Clave vieja | Valor | Se traduce a |
|---|---|---|
| `identifier_format` | `"camel case"` | `identifier-naming: "camel-case"` |
| `identifier_format` | `"snake case"` | `identifier-naming: "snake-case"` |
| `mandatory-variable-or-literal-in-println` | `true` | `println-argument: true` |
| `mandatory-variable-or-literal-in-println` | `false` | ausente |
| `mandatory-variable-or-literal-in-readInput` | `true` | `read-input-argument: true` |
| `mandatory-variable-or-literal-in-readInput` | `false` | ausente |

- **Si el pedido usa alguna, la respuesta `200` trae el header `Deprecation: @1793836800`** ([RFC 9745](https://www.rfc-editor.org/rfc/rfc9745)), el mismo que `/format`.
- **Dos claves que definen la misma regla con valores distintos son un `400`,** por ejemplo `identifier_format: "snake case"` e `identifier-naming: "camel-case"`. Con el mismo valor no hay error.

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, haya o no violaciones | `{"violations":[...],"ignoredRules":[...]}` |
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |
| `400` | Las reglas son inválidas | Problem Details con `rule` (ver abajo) |
| `400` | Falta un campo del body | Problem Details de Spring |

Lista vacía = sin violaciones. Cada violación trae la regla, dónde empieza y su severidad. `ignoredRules` siempre viene, vacía si no se ignoró nada:

```json
{"violations":[{"rule":"identifier-naming","message":"El identificador 'my_var' no sigue la convención de nombres configurada.","line":1,"column":5,"severity":"WARNING"}],"ignoredRules":[]}
```

| `rule` | `severity` |
|---|---|
| `identifier-naming` | `WARNING` |
| `println-argument` | `ERROR` |
| `read-input-argument` | `ERROR` |
| `syntax` | `ERROR`. No es una regla: es un error de sintaxis |

`line` y `column` son **base 1**, como en `/validate`.

Las reglas inválidas traen en `rule` el id (o la clave vieja) que falló:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'",
  "instance": "/lint",
  "message": "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'",
  "rule": "identifier-naming"
}
```

- Un id que no existe: `Regla de lint desconocida: 'no-existe'`
- Una convención que no existe: `La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'`
- Una convención que no existe en la clave vieja: `La regla 'identifier_format' tiene que ser 'camel case' o 'snake case'`
- Algo que no es booleano en un flag: `La regla 'println-argument' solo acepta true`
- Algo que no es booleano en una clave vieja: `La regla 'mandatory-variable-or-literal-in-println' tiene que ser true o false`
- Dos claves con valores distintos para la misma regla: `'identifier_format' y 'identifier-naming' definen 'identifier-naming' con valores distintos`. Acá `rule` es el id nuevo.

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
