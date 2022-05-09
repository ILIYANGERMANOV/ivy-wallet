# Ivy Developer Guidelines

A short guide _(that'll evolve with time)_ with one and only goal - to **make you a better developer.**

[![PRs are welcome!](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ILIYANGERMANOV/ivy-wallet/blob/main/CONTRIBUTING.md)
[![Feedback is welcome!](https://img.shields.io/badge/feedback-welcome-brightgreen)](https://t.me/+ETavgioAvWg4NThk)
[![Proposals are highly appreciated!](https://img.shields.io/badge/proposals-highly%20appreciated-brightgreen)](https://t.me/+ETavgioAvWg4NThk)

## Ivy Architecture (FRP)

The Ivy Architecture follows the Functional Reactive Programming (FRP) principles. A good example for them is [The Elm Architecture.](https://guide.elm-lang.org/architecture/)

### Architecture graph

```mermaid
graph TD;

android(Android System)
user(User)
view(UI)
event(Event)
viewmodel(ViewModel)
action(Action)
pure(Pure)

event -- Propagated --> viewmodel
viewmodel -- Triggers --> action
viewmodel -- "UI State (Flow)" --> view
action -- "Abstacts IO" --> pure
action -- "Composition" --> action
pure -- "Composition" --> pure
pure -- "Computes" --> action
action -- "Data" --> viewmodel

user -- Interracts --> view
view -- Produces --> event
android -- Produces --> event
```

### 0. Data Model

The Data Model in Ivy drives clear separation between `domain` pure data required for business logic w/o added complexity, `entity` database data, `dto` _(data transfer object)_ JSON representation for network requests and `ui` data which we'll displayed.

Learn more at [Android Developers Architecture: Entity](https://www.youtube.com/watch?v=cfak1jDKM_4).

#### Data Model

```mermaid
graph TD;

data(Data)
entity(Entity)
dto(DTO)
ui_data(UI Data)

ui(UI)
network(Network)
db(Database)
viewmodel(ViewModel)
domain("Domain (Action, Pure)")

network -- Fetch --> dto -- Send --> network
dto --> data

db -- Retrieve --> entity -- Persist --> db
entity --> data

data --> entity
data --> dto

data -- Computation input --> domain
domain -- Computation output --> viewmodel
viewmodel -- Transform --> ui_data
ui_data -- "UI State (Flow)" --> ui

```

#### Example

- `DisplayTransaction`
  - UI specific fields
- `Transaction`
  - pure domain data
- `TransactionEntity`
  - has `isSynced`, `isDeletedFlags` db specific fields (Room DB anontations)
- `TransactionDTO`
  - exactly what the API expects/returns (JSON)

> Motivation: This separation **reduces complexity** and **provides flexibility** for changes.

### 1. Event (UI interaction or system)

An `Event` is generated from either user interaction with the UI or a system subscription _(e.g. Screen start, Time, Random, Battery level)_.

```mermaid
graph TD;
user(User)
world(Outside World)
system(System Event)
ui(UI)
event(Event)

user -- Interracts --> ui
world -- Triggers --> system

ui -- Produces --> event
system -- Produces --> event
```

> Note: There are infinite user inputs and outside world signals.

### 2. ViewModel (mediator)

Triggers `Action` for incoming `Event`, transforms the result to `UI State` and propagates it to the UI via `Flow`.

```mermaid
graph TD;

event(Event)
viewmodel(ViewModel)
action(Actions)
ui(UI)

event -- Incoming --> viewmodel
viewmodel -- "Action Input" --> action
action -- "Action Output" --> viewmodel
viewmodel -- "UI State (Flow)" --> ui


```

### 3. Action (domain logic with side-effects)

Actions accept `Action Input`, handles `threading`, abstract `side-effects` (IO) and executes specific domain logic by **compising** `pure` functions or other `actions`.

#### Action Types

- `FPAction()`: declaritve FP style _(preferable)_
- `Action()`: imperative OOP style

**Action Lifecycle:**

```mermaid
graph TD;

input(Action Input)
output(Action Output)
pure(Pure Functions)
action(Action)

io(IO)
dao(Datbase)
network(Network)
side-effect(Side-Effect)

side-effect -- any --> io
dao -- DAOs --> io
network -- Retrofit --> io
io -- DI --> action

action -- Composition --> action
action -- Threading --> action

input --> action
action -- abstracted IO --> pure -- Result --> action
action -- Final Result --> output
```

> `Actions` are very similar to the "use-cases" from the standard "Clean Code" architecture.
>
> You can compose `actions` and `pure` functions by using `then`.

### 4. Pure (domain logic with pure code)

The `pure` layer as the name suggests must consist of only pure functions without side-effects. If the business logic requires, **side-effects must be abstracted**.

#### Code Example

```Kotlin
//domain.action
class ExchangeAct @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao,
) : FPAction<ExchangeAct.Input, Option<BigDecimal>>() {
    override suspend fun Input.compose(): suspend () -> Option<BigDecimal> = suspend {
        exchange(
            data = data,
            amount = amount,
            getExchangeRate = exchangeRateDao::findByBaseCurrencyAndCurrency then {
                it?.toDomain()
            }
        )
    }

    data class Input(
        val data: ExchangeData,
        val amount: BigDecimal
    )
}


//domain.pure
@Pure
suspend fun exchange(
    data: ExchangeData,
    amount: BigDecimal,

    @SideEffect
    getExchangeRate: suspend (baseCurrency: String, toCurrency: String) -> ExchangeRate?,
): Option<BigDecimal> {
  //PURE IMPLEMENTATION
}
```

### 5. UI (@Composable)

Renders the `UI State` that the user sees, handles `user input` and transforms it to `events` which are propagated to the `ViewModel`. **Do NOT perform any business logic or computations.**

```mermaid
graph TD;

user(User)
uiState("UI State (Flow)")
ui("UI (@Composable)")
event(Event)
viewmodel(ViewModel)

user -- Interracts --> ui
ui -- Produces --> event 
event -- "onEvent()" --> viewmodel
viewmodel -- "Action(s)" --> uiState
uiState -- "Flow#collectAsState()" --> ui
```

> Exception: The UI layer may perform in-app navigation **`navigation().navigate(..)`** to reduce boiler-plate and complexity.

### 6. IO (side-effects)

Responsible for the implementation of IO operations like persistnece, network requests, randomness, date & time, etc.

- **Room DB**, local persistence
- **Shares Preferences**, local persistence
  - key-value pairs persistence
  - _will be migrated to DataStore_
- **Retrofit**, Network Requests (REST)
  - send requests
  - parse response JSON with GSON
  - transform network errors to `NetworkException`
- **Randomness**
  - `UUID` generation
- **Date & Time**
  - current Date & Time (`timeNowUtc`, `dateNowUtc`)
  - Date & Time formatting using user's `Locale`

### 7. Andoid System (side-effects)

Responsible for the interaction with the Android System like launching `Intent`, sending `notification`, receiving `push messages`, `biometrics`, etc.

---

_Version 1.0.0_

_Feedback and proposals are highly appreciated! Let's spark techincal discussion and make Ivy and the Android World better! :rocket:_
