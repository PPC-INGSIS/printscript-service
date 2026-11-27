# PrintScript

Servicio de **Snippet Searcher** encargado del lenguaje PrintScript. Valida, formatea, lintea y ejecuta código PrintScript usando la librería del TP1.

No tiene base de datos: no guarda nada entre pedidos. La única excepción es la [ejecución interactiva](#ws-executeinteractive), que vive en memoria mientras dura su conexión. Tampoco sabe de usuarios ni de permisos. Lo llama el servicio de Snippets, nunca la UI.

## Stack

| | |
|---|---|
| Lenguaje | Kotlin 2.3 sobre Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, WebSocket, Actuator) |
| Lenguaje PrintScript | `printscript:runner` 1.1.1 (librería del TP1) |
| Build | Gradle (Kotlin DSL) |
| Calidad | ktlint, detekt, JaCoCo |
| Tests | JUnit 5 |

## Requisitos

- Java 21
- Un token de GitHub con `read:packages` (ver [Credenciales de GitHub Packages](#credenciales-de-github-packages))

No necesita Docker.

## Correrlo localmente

Necesita las variables de Auth0: copiá `.env.example` como `.env` y completalo con los mismos valores que el `.env` de snippet-service.

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

## New Relic

La imagen de Docker trae el agente de Java de New Relic. Se engancha en la JVM al arrancar, sin cambios en el código, y manda:

- **Las trazas de cada pedido.** El trace id viaja en los headers HTTP: un pedido que entra a Snippets y llama a PrintScript queda en una sola traza.
- **La latencia de cada endpoint**, con percentiles (p99 incluido).
- **Los logs de la app**, con el trace id.

Se configura con estas variables de entorno, que pone el repo de infraestructura:

| Variable | |
|---|---|
| `NEW_RELIC_LICENSE_KEY` | Key de tipo *Ingest - License*, una por entorno |
| `NEW_RELIC_APP_NAME` | `printscript-dev`, `printscript-prod` o `printscript-local` |
| `NEW_RELIC_AGENT_ENABLED` | En local queda en `false` salvo que pongas tu key |

- **Con `bootRun` no hay agente:** solo está en la imagen.
- Lo que no cambia por entorno está en `newrelic/newrelic.yml`.

Para probarlo en local con el compose de infraestructura:

1. Creá una key de tipo *Ingest - License* en New Relic.
2. En el `.env` de infraestructura, poné `NEW_RELIC_LICENSE_KEY=<tu-key>` y `NEW_RELIC_AGENT_ENABLED=true`.
3. Levantá el compose y hacé pedidos a `127.0.0.1:8082`. Usá `127.0.0.1` y no `localhost`: si tenés el servicio corriendo con `bootRun`, `localhost` puede ir a ese.
4. En un par de minutos aparece `printscript-local` en New Relic.

## Errores

Los errores del pedido (4xx) responden con Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) y `Content-Type: application/problem+json`. **Los clientes tienen que leer `detail`.**

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1",
  "instance": "/format"
}
```

- **`type` no viene en el body.** Según el RFC, si falta equivale a `about:blank`.

Si el código supera el tamaño máximo (ver [Límites](#límites)), los cuatro endpoints que lo reciben responden `413` con el mismo formato. Es determinístico: reintentar el mismo código da lo mismo.

```json
{
  "title": "Payload Too Large",
  "status": 413,
  "detail": "El código supera el tamaño máximo de 64 KB",
  "instance": "/execute"
}
```

Si el código tiene demasiados niveles de anidamiento, como miles de paréntesis uno dentro de otro, el parser de la librería desborda el stack. No es un error del pedido: `/validate`, `/execute` y `/lint` responden `200` con este error, igual que con cualquier otro error del código, y `/execute/interactive` termina con `finished` y el mismo error.

```json
{"message":"El código tiene demasiados niveles de anidamiento","line":null,"column":null}
```

- **Viene sin posición.** `line` y `column` llegan en `null`: el desborde corta el parser en cualquier punto, sin un error de la librería que diga dónde.
- **No hay un número fijo de niveles.** Depende del tamaño del stack de la JVM, así que el mensaje no da un número.
- **Dónde aparece:** en `/validate`, como único elemento de `errors`. En `/execute`, en `error`, con lo impreso hasta ahí en `outputs`. En `/lint`, en `error`, con las violaciones juntadas hasta ahí en `violations`.
- **`/format` no se ve afectado.** El formateador trabaja sobre tokens, sin parsear, así que el código anidado se formatea igual.

## Límites

Acotan cuánto código procesa el servicio y cuánto producen y duran las ejecuciones. Los números de los mensajes salen de estas properties.

| Property | Default | Qué limita | Pasarse responde |
|---|---|---|---|
| `printscript.limits.max-code-size` | `64KB` | Bytes UTF-8 de `code` en `/validate`, `/execute`, `/format`, `/lint` y `/execute/interactive` | `413`. En `/execute/interactive`, `rejected` y cierre `1009` |
| `printscript.limits.max-output-size` | `1MB` | Caracteres impresos por `/execute` o por una ejecución interactiva (1 MB = 1048576 caracteres) | `200` con `error`. En `/execute/interactive`, `finished` con `error` |
| `printscript.limits.execution-timeout` | `5s` | Duración de `/execute` | `200` con `error` |
| `printscript.interactive.input-timeout` | `60s` | Espera de cada input en `/execute/interactive` | `finished` con `error` |
| `printscript.interactive.max-duration` | `10m` | Duración total de una ejecución interactiva, desde el `start` | `finished` con `error` |
| `printscript.interactive.max-sessions` | `16` | Conexiones abiertas a la vez a `/execute/interactive`, tengan o no una ejecución | `rejected` y cierre `1013` al conectar |
| `printscript.interactive.max-pending-inputs` | `32` | Inputs encolados que el snippet todavía no leyó | `rejected` y cierre `1008` |
| `printscript.interactive.max-message-size` | `256KB` | Tamaño de un mensaje que llega a `/execute/interactive` | Cierre `1009`, sin `rejected` |
| `printscript.interactive.send-timeout` | `20s` | Espera de cada envío a un cliente de `/execute/interactive` que no lee | Se corta la conexión, sin `rejected` ni `finished`, y se cancela la ejecución |
| `printscript.interactive.start-timeout` | `15s` | Espera del `start` desde que se abre la conexión a `/execute/interactive` | `rejected` y cierre `1008` |

- **Los límites de `printscript.interactive` tienen que ser mayores a 0.** Si alguno no lo es, el servicio no arranca y el error nombra la property.
- **El tamaño de `code` se chequea después de leer el body.** En `/execute`, `/format` y `/lint`, Jackson ya armó el `String` cuando llega el chequeo: el límite acota lo que se procesa, no lo que se recibe. `/validate` cuenta los bytes mientras lee el stream y corta apenas pasa el máximo.
- **El timeout corta la ejecución en el próximo `println` o `readInput`.** Al vencer, el servicio responde con el error de tiempo límite e interrumpe el thread de la ejecución. Entre uno y otro no se puede frenar: el intérprete no chequea interrupciones, así que un cálculo sin `println` ni `readInput` sigue corriendo hasta terminar.
- **Una ejecución vencida ocupa el pool hasta su próximo `println` o `readInput`.** `/execute` corre en el `applicationTaskExecutor` de Spring Boot: 8 threads y cola sin límite. Si 8 ejecuciones vencidas siguen corriendo a la vez sin llegar a uno, los pedidos nuevos esperan en la cola, y esa espera cuenta para su timeout. Sin loops y con 64 KB de código, solo puede tardar un snippet que crece exponencialmente, y ese se queda sin memoria en segundos, así que el pool se libera rápido.

## Autenticación

Todos los endpoints, salvo `/actuator/health`, exigen un token de Auth0 con el scope `read:snippets` (presentación de Auth0, diap. 48: "validar el token en cada servicio" y "chequear el scope"):

| Pedido | Respuesta |
|---|---|
| Sin token, o con un token inválido o vencido | `401` |
| Con un token válido sin `read:snippets` | `403` |
| Con un token válido con `read:snippets` | Lo que corresponda al endpoint |

Es `read:snippets` en todos porque PrintScript no guarda ni modifica nada: procesa lo que recibe. La escritura la chequea Snippets con `write:snippets`.

Snippets reenvía el token del usuario en cada llamada. Para probar a mano, sacá un token como se explica en el README de snippet-service y agregá `-H "Authorization: Bearer $TOKEN"` a los `curl` de abajo.

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
| `413` | El código supera el tamaño máximo | Problem Details (ver [Errores](#errores)) |
| `415` | El `Content-Type` no es `text/plain` | Problem Details de Spring |

Lista vacía = el código es válido. Cada error trae dónde empieza:

```json
{"errors":[{"message":"Se esperaba un valor, un identificador o '('","line":1,"column":17}]}
```

`line` y `column` son **base 1**, tal como las da la librería: la primera letra del archivo es `1:1`.

A tener en cuenta:

- **Si el código tiene demasiados niveles de anidamiento, `errors` trae solo ese error, sin posición:** `El código tiene demasiados niveles de anidamiento`. Ver [Errores](#errores).

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
| `413` | El código supera el tamaño máximo | Problem Details (ver [Errores](#errores)) |

Si terminó bien, `error` es `null`:

```json
{"outputs":["Nombre: ","Hola Juan"],"error":null}
```

Un error de sintaxis, uno de ejecución, la falta de inputs, demasiado anidamiento o pasarse de la salida o del tiempo límite **no** son errores del pedido: responden `200` con el error y lo impreso hasta ese momento.

```json
{"outputs":["antes"],"error":{"message":"...","line":2,"column":9}}
```

A tener en cuenta:

- **El prompt de `readInput` sale como output.** La librería lo imprime antes de leer, así que `readInput("Nombre: ")` agrega `"Nombre: "` a `outputs`.
- **`readEnv` nunca lee las variables del servidor.** Cualquier `readEnv` corta la ejecución con `La variable de entorno 'X' no está definida.` y su posición.
- **Si faltan inputs, el error viene sin posición.** `line` y `column` llegan en `null`: la librería no le pasa la posición a quien da los inputs, y cuando el error llega al servicio ya no hay forma de saber en qué sentencia estaba. El mensaje dice cuántos se enviaron: `Faltan inputs: el snippet pidió más de los que se enviaron (se envió 1)`.
- **Si la salida pasa el máximo, la ejecución se corta.** El error viene sin posición: `La salida superó el máximo de 1048576 caracteres`. `outputs` trae lo impreso hasta ahí, sin el texto que lo pasó. Los prompts de `readInput` también cuentan.
- **Si la ejecución pasa el tiempo límite, se corta igual.** El error viene sin posición: `La ejecución superó el tiempo límite de 5 s`. `outputs` trae lo impreso hasta ese momento.
- **Si el código tiene demasiados niveles de anidamiento, el error viene sin posición:** `El código tiene demasiados niveles de anidamiento`. `outputs` trae lo impreso hasta ahí. Ver [Errores](#errores).

### `WS /execute/interactive`

Ejecuta código y va mandando cada output apenas se imprime; pide cada input recién cuando el snippet lo lee. Snippets la usa para que el usuario responda los `readInput` en el momento, en vez de mandar todos los inputs de antemano como en `/execute`. Igual que `/execute`, recibe el contenido y no una ruta.

```bash
websocat -H "Authorization: Bearer $TOKEN" ws://localhost:8082/execute/interactive
```

- **Se conecta Snippets, nunca el browser.** El handshake es un `GET` y pide el mismo token que el resto (ver [Autenticación](#autenticación)): sin token responde `401` y sin el scope, `403`.
- **Una conexión es una ejecución.** Al terminar, el servidor cierra; para volver a ejecutar se abre otra.

**Protocolo**

Mensajes de texto en JSON, uno por mensaje, con el campo `type`.

| Lo manda | Mensaje | Cuándo |
|---|---|---|
| Cliente | `{"type":"start","code":"...","version":"1.1"}` | Primer mensaje, una sola vez. Arranca la ejecución |
| Cliente | `{"type":"input","value":"Juan"}` | Responde un `readInput`. Si llega antes de que lo pidan, queda encolado |
| Servidor | `{"type":"output","text":"Nombre: "}` | Cada vez que el snippet imprime |
| Servidor | `{"type":"input_request"}` | El snippet espera un input |
| Servidor | `{"type":"finished","error":null}` | Terminó. Después cierra con `1000` |
| Servidor | `{"type":"rejected","detail":"..."}` | El pedido es inválido. Después cierra (ver abajo) |

`error` es el mismo que en `/execute`: `null` si terminó bien, o el error con su posición.

```json
{"type":"finished","error":{"message":"...","line":2,"column":9}}
```

Una conversación completa, con el snippet del ejemplo de `/execute` (`→` manda el cliente, `←` el servidor):

```text
→ {"type":"start","code":"let nombre: string = readInput(\"Nombre: \");\nprintln(\"Hola \" + nombre);","version":"1.1"}
← {"type":"output","text":"Nombre: "}
← {"type":"input_request"}
→ {"type":"input","value":"Juan"}
← {"type":"output","text":"Hola Juan"}
← {"type":"finished","error":null}
← cierre 1000
```

**Rechazos**

Un pedido inválido responde `rejected` con el motivo en `detail` y cierra la conexión, lo que cancela la ejecución si había una.

| Caso | `detail` | Cierre |
|---|---|---|
| El mensaje no es JSON | `El mensaje no es un JSON válido` | `1008` |
| Falta `type` | `Falta el campo 'type'` | `1008` |
| `type` desconocido | `Tipo de mensaje desconocido: 'stop'. Se espera 'start' o 'input'` | `1008` |
| Falta un campo, o no es texto | `El mensaje 'start' necesita el campo 'code' como texto` | `1008` |
| `input` antes de `start` | `El primer mensaje tiene que ser 'start'` | `1008` |
| Un segundo `start` | `La ejecución ya arrancó: no se puede mandar otro 'start'` | `1008` |
| La versión no existe | `La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1` | `1008` |
| El código supera el tamaño máximo | `El código supera el tamaño máximo de 64 KB` | `1009` |
| La cola de inputs está llena | `Hay demasiados inputs sin consumir: el máximo es 32` | `1008` |
| Hay demasiadas conexiones abiertas, al conectar | `Se alcanzó el máximo de conexiones abiertas a la vez (16). Probá de nuevo más tarde` | `1013` |
| El `start` no llega a tiempo | `No llegó el 'start' en 15 s` | `1008` |

A tener en cuenta:

- **Cerrar la conexión, del lado que sea, cancela la ejecución.**
- **Un cliente que deja de leer corta la conexión.** Si un envío queda trabado más de `send-timeout`, se da por fallido y se cierra la sesión, sin `rejected` ni `finished`, lo que cancela la ejecución.
- **Los errores de la ejecución no son rechazos.** Un error de sintaxis o de ejecución, demasiado anidamiento, pasarse de la salida, no recibir un input a tiempo o pasarse de la duración máxima terminan con `finished` y el error, igual que en `/execute`. Los de límites vienen sin posición: `No se recibió un input en 60 s`, `La ejecución superó la duración máxima de 600 s`.
- **Un mensaje de más de `max-message-size` cierra con `1009`, sin `rejected`.** Lo corta el servidor WebSocket antes de que llegue al servicio.
- **El prompt de `readInput` sale como `output`**, antes del `input_request`. `readEnv` nunca lee las variables del servidor, como en `/execute`.
- **La ejecución vive en memoria, en una sola réplica.** Un deploy o un reinicio corta las ejecuciones abiertas: la conexión se cierra y el usuario tiene que volver a ejecutar. No hay reconexión.
- **El `start` tiene plazo, la ejecución no.** Una conexión que no manda el `start` en `start-timeout` se rechaza y libera su lugar de `max-sessions`. Una vez que arrancó, la ejecución dura lo que permitan `input-timeout` y `max-duration`.

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

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, **también si el código tiene un error** | `{"formatted":...,"error":...,"ignoredRules":[...]}` |
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |
| `400` | Las reglas son inválidas | Problem Details con `rule` (ver abajo) |
| `400` | Falta un campo del body | Problem Details de Spring |
| `413` | El código supera el tamaño máximo | Problem Details (ver [Errores](#errores)) |

Si terminó bien, `error` es `null`. `ignoredRules` siempre viene, vacía si no se ignoró nada:

```json
{"formatted":"let x:number = 1;","error":null,"ignoredRules":[]}
```

Un error en el código **no** es un error del pedido: responde `200` con el error y `formatted` en `null`.

```json
{"formatted":null,"error":{"message":"...","line":2,"column":19},"ignoredRules":[]}
```

Las reglas inválidas traen en `rule` el id que falló:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12",
  "instance": "/format",
  "rule": "if-indent"
}
```

- Un id que no existe: `Regla de formato desconocida: 'no-existe'`
- Un valor que no está entre los permitidos: `La regla 'space-before-colon' tiene que ser 'always' o 'never'`
- Algo que no es un entero donde va un entero: `La regla 'if-indent' espera un número entero`
- Un entero fuera de rango: `La regla 'if-indent' acepta valores entre 0 y 8, se recibió 12`
- Algo que no es booleano en un flag: `La regla 'line-break-after-statement' solo acepta true`

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

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | Siempre que el pedido esté bien, haya o no violaciones | `{"violations":[...],"error":...,"ignoredRules":[...]}` |
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |
| `400` | Las reglas son inválidas | Problem Details con `rule` (ver abajo) |
| `400` | Falta un campo del body | Problem Details de Spring |
| `413` | El código supera el tamaño máximo | Problem Details (ver [Errores](#errores)) |

Lista vacía = sin violaciones. Cada violación trae la regla, dónde empieza y su severidad. `ignoredRules` siempre viene, vacía si no se ignoró nada:

```json
{"violations":[{"rule":"identifier-naming","message":"El identificador 'my_var' no sigue la convención de nombres configurada.","line":1,"column":5,"severity":"WARNING"}],"error":null,"ignoredRules":[]}
```

`error` es `null` salvo que el análisis se corte antes de terminar, y hoy eso solo pasa cuando el código tiene demasiados niveles de anidamiento (ver [Errores](#errores)). En ese caso `violations` trae las juntadas hasta ahí:

```json
{"violations":[...],"error":{"message":"El código tiene demasiados niveles de anidamiento","line":null,"column":null},"ignoredRules":[]}
```

| `rule` | `severity` |
|---|---|
| `identifier-naming` | `WARNING` |
| `println-argument` | `ERROR` |
| `read-input-argument` | `ERROR` |
| `syntax` | `ERROR`. No es una regla: es un error de sintaxis |

`line` y `column` son **base 1**, como en `/validate`.

Las reglas inválidas traen en `rule` el id que falló:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'",
  "instance": "/lint",
  "rule": "identifier-naming"
}
```

- Un id que no existe: `Regla de lint desconocida: 'no-existe'`
- Una convención que no existe: `La regla 'identifier-naming' tiene que ser 'camel-case' o 'snake-case'`
- Algo que no es booleano en un flag: `La regla 'println-argument' solo acepta true`

A tener en cuenta:

- **Un error de sintaxis no corta el análisis.** Aparece en `violations` con `rule` `syntax`, y la librería sigue con la sentencia siguiente: las violaciones de las demás sentencias salen igual.
- **Si el código tiene demasiados niveles de anidamiento, el análisis se corta.** `error` trae `El código tiene demasiados niveles de anidamiento`, sin posición, y `violations` las juntadas hasta ahí. Ver [Errores](#errores).

### `GET /versions`

Devuelve las versiones de PrintScript que soporta el servicio y cuál es la última. Snippets la usa para completar `version` al crear un snippet, sin tener una versión fija en su código.

```bash
curl localhost:8082/versions
```

Sin parámetros. Siempre responde `200`:

```json
{"versions":[{"id":"1.0"},{"id":"1.1"}],"latest":"1.1"}
```

- **`versions` va de la más vieja a la más nueva.** `latest` es la última de la lista.
- **Cada versión es un objeto y no un string**, para poder sumarle campos más adelante sin romper el contrato.

### `GET /rules/format?version={id}` y `GET /rules/lint?version={id}`

Devuelven el catálogo de reglas de formato y de lint: qué reglas hay, desde qué versión, qué valores aceptan y qué valor proponer al activarlas. Snippets lo usa para armar las pantallas de reglas sin tener la lista en su código. Sale de las mismas reglas que validan `/format` y `/lint`.

```bash
curl "localhost:8082/rules/format?version=1.1"
```

| Parámetro | |
|---|---|
| `version` | Opcional. Versión de PrintScript: `1.0` o `1.1`. Sin el parámetro, la última |

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `200` | La versión existe o no se mandó | `{"version":"1.1","rules":[...]}` |
| `400` | La versión no existe | Problem Details (ver [Errores](#errores)) |

```json
{
  "version": "1.1",
  "rules": [
    {
      "id": "if-brace-position",
      "description": "Dónde va la llave que abre un if",
      "since": "1.1",
      "schema": { "type": "string", "enum": ["same-line", "next-line"], "default": "same-line" }
    },
    {
      "id": "line-break-after-statement",
      "description": "Salto de línea después de cada sentencia",
      "since": "1.0",
      "schema": { "type": "boolean", "const": true }
    }
  ]
}
```

- **`version` es la versión usada:** la pedida, o la última si no se mandó.
- **Solo vienen las reglas de esa versión** (`since` menor o igual). Con `1.0`, `/rules/format` no trae `if-brace-position` ni `if-indent`, y `/rules/lint` no trae `read-input-argument`.
- **El orden es el de las tablas de reglas de `/format` y `/lint`.**
- **Solo vienen los ids de las reglas:** las claves de la librería no aparecen.
- **`description` está en castellano y puede cambiar.** El que identifica a la regla es `id`.

`schema` es [JSON Schema](https://json-schema.org/) y depende del tipo de la regla:

| Tipo | `schema` |
|---|---|
| Opciones | `{"type":"string","enum":[...],"default":...}` |
| Entero | `{"type":"integer","minimum":n,"maximum":m,"default":...}` |
| Flag | `{"type":"boolean","const":true}`. Sin `default` |

- **`default` es el valor que se propone al activar la regla, no uno que se aplique solo.** Una regla que no se manda a `/format` o `/lint` no se aplica.

### `POST /rules/format/validate` y `POST /rules/lint/validate`

Validan reglas de formato y de lint sin mandar código: que cada id exista y que su valor sea del tipo y del rango correctos. Snippets los usa antes de guardar las reglas de un usuario. Validan con lo mismo que `/format` y `/lint`, así que un error da la misma respuesta que en esos endpoints.

```bash
curl -X POST localhost:8082/rules/format/validate \
  -H "Content-Type: application/json" \
  -d '{"rules":{"if-indent":4,"space-before-colon":"never"}}'
```

| Campo | |
|---|---|
| `rules` | Las reglas, por id, con el mismo formato que en `/format` y `/lint`. `{}` es válido |

**Respuestas**

| Status | Cuándo | Body |
|---|---|---|
| `204` | Todas las reglas son válidas | Sin body |
| `400` | Una regla es inválida | Problem Details con `rule` (ver abajo) |
| `400` | Falta `rules` en el body | Problem Details de Spring |

La regla inválida viene en `rule`, con el mismo `detail` que en `/format` y `/lint` (ver los mensajes en esas secciones):

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Regla de lint desconocida: 'no-existe'",
  "instance": "/rules/lint/validate",
  "rule": "no-existe"
}
```

- **No reciben versión.** Que una regla sea válida no depende de la versión, así que valen todas las reglas, también las de `1.1`. `if-indent` es válida aunque el snippet sea `1.0`: ahí `/format` no la aplica y la devuelve en `ignoredRules`.
- **Solo informan la primera regla inválida**, como `/format` y `/lint`. Si hay más de una, después de corregirla aparece la siguiente.
- **Un flag en `false` es válido:** equivale a no mandarlo.

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
