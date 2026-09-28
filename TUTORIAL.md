# Build an NBA Prop Lines API with Spring Boot

*A hands-on tutorial for Spring Boot interview prep. Spring Boot 4.1.1, Java 21, Maven.*

You'll build `nba-props`, a REST API for NBA player prop lines ("Stephen Curry, over/under 27.5 points, -110/-110"). Each chapter adds one feature and teaches the Spring concepts behind it. By the end, you'll have built, tested, secured, and containerized a small but realistic backend, and you'll be able to explain why every piece is there.

## How to use this tutorial

- **Work in order.** Each chapter builds on the one before it.
- **Try before you peek.** Every step gives you a task and some hints. The full solution is collapsed under **Solution**; open it once you've tried, or to compare.
- **Run the tests.** Each step ends with a test and the command to run it. A green build means you're ready to move on.
- **Answer the quick checks.** Each chapter ends with short "what" and "why" questions. Answer them out loud, as you would in an interview, before you expand the answers.
- **Compare with the reference when you're stuck.** The `tutorial-solution` branch has one commit per chapter:
  - `git log --oneline tutorial-solution` lists them.
  - `git show <commit>` shows exactly what that chapter changed.
  - `git diff <commit> -- nba-props/src` compares your files with the reference.

Conventions:

- Commands are for **PowerShell**. Run them from the `nba-props` folder unless a step says otherwise.
- Always type `curl.exe`, not `curl`. In Windows PowerShell 5.1, `curl` is an alias for `Invoke-WebRequest`, which behaves differently.
- File paths are relative to the repository root (`NBAops/`).
- **Boot 4 note** callouts mark places where material you find online (mostly Spring Boot 2 or 3) will look different from what works here.
- Player names and teams are sample data and may be out of date.

## Prerequisites

- **JDK 21.** `java -version` should print 21.
- **VS Code with the Java extensions**, or any Java IDE.
- **Git.**
- **Docker Desktop**, for chapters 8 and 11 only.
- **A free API key from [The Odds API](https://the-odds-api.com)**, optional, only for importing live lines in chapter 10.

## Suggested pacing

Plan on about 20 hours, or roughly one chapter per day.

| Week | Chapters | Topics |
|---|---|---|
| 1 | 0–4 | Boot basics, dependency injection, REST, JPA |
| 2 | 5–8 | Validation, relationships and transactions, testing, profiles and PostgreSQL |
| 3 | 9–11 | Security, calling an external API, Actuator and Docker |

If your interview comes sooner, do chapters 0–7 first. They're the core; 8–11 build on them.

## What you'll build

| Method | Path | Who can call it | What it does |
|---|---|---|---|
| GET | `/api/players` | anyone | List players (optional `?team=`) |
| GET | `/api/players/{id}` | anyone | Get one player |
| POST | `/api/players` | admin | Create a player |
| GET | `/api/prop-lines` | anyone | List lines (optional `?playerId=` and `?statType=`) |
| GET | `/api/prop-lines/{id}` | anyone | Get one line |
| POST | `/api/prop-lines` | admin | Create a line |
| PUT | `/api/prop-lines/{id}` | admin | Replace a line |
| DELETE | `/api/prop-lines/{id}` | admin | Delete a line |
| POST | `/api/admin/odds/import` | admin | Import lines from The Odds API |
| GET | `/actuator/health` | anyone | Health check |

How a request moves through the finished app:

```text
HTTP request
     |
     v
+---------------------------+
| Security filter chain     |  chapter 9: who are you, and are you allowed?
+---------------------------+
     |
     v
+---------------------------+
| DispatcherServlet         |  chapter 1: which controller method handles this?
+---------------------------+
     |
     v
Controller  --->  Service  --->  Repository  --->  Database
(HTTP, DTOs)      (rules,        (Spring Data      (H2, later
                  transactions)   JPA)              PostgreSQL)
                     |
                     +--->  OddsClient  --->  The Odds API (chapter 10)
```

## Contents

- [Chapter 0: Setup and tour](#chapter-0-setup-and-tour)
- [Chapter 1: Your first endpoint](#chapter-1-your-first-endpoint)
- [Chapter 2: Beans and dependency injection](#chapter-2-beans-and-dependency-injection)
- [Chapter 3: Designing a REST API](#chapter-3-designing-a-rest-api)
- [Chapter 4: Persistence with Spring Data JPA and H2](#chapter-4-persistence-with-spring-data-jpa-and-h2)
- [Chapter 5: Validation and error handling](#chapter-5-validation-and-error-handling)
- [Chapter 6: Prop lines, relationships and transactions](#chapter-6-prop-lines-relationships-and-transactions)
- [Chapter 7: Integration testing](#chapter-7-integration-testing)
- [Chapter 8: Profiles and PostgreSQL](#chapter-8-profiles-and-postgresql)
- [Chapter 9: Spring Security](#chapter-9-spring-security)
- [Chapter 10: Importing real odds with RestClient](#chapter-10-importing-real-odds-with-restclient)
- [Chapter 11: Actuator and Docker](#chapter-11-actuator-and-docker)
- [Appendix A: Annotation cheat sheet](#appendix-a-annotation-cheat-sheet)
- [Appendix B: Boot 3 vs Boot 4 in material you find online](#appendix-b-boot-3-vs-boot-4-in-material-you-find-online)
- [Appendix C: Troubleshooting](#appendix-c-troubleshooting)

## Chapter 0: Setup and tour

**Goal:** Start from a clean Spring Boot project on a new `tutorial` branch, and understand every file in it.

**You'll learn:** the Maven wrapper, the parent POM and dependency management, starters, `@SpringBootApplication`, embedded Tomcat, and auto-configuration.

### Step 0.1: Put your earlier experiments away

Your `nba-props` folder still has your earlier experiments: `Player.java`, `PlayerController.java`, and a few others, plus an uncommitted change to `pom.xml`. The new files were never committed, so deleting them would lose them for good. Stash them instead. Git keeps a copy, and your folder becomes clean.

Run these from the repository root (`NBAops`), not from `nba-props`:

```powershell
git status
git stash push --include-untracked -m "old player experiments" -- nba-props
git switch -c tutorial master
git status
```

- `git stash push --include-untracked -- nba-props` saves every change under `nba-props/`, including files git wasn't tracking yet, and removes them from the folder. Files outside `nba-props/`, like `CLAUDE.md` and this `TUTORIAL.md`, stay where they are.
- `git switch -c tutorial master` creates a branch called `tutorial` from `master` and switches to it. `master` holds the untouched project from Spring Initializr, on Java 21. Your Java 25 upgrade branch stays as it is.

Git may print several `warning: in the working copy of '...', LF will be replaced by CRLF` lines. They're harmless: git is only telling you how it stores line endings on Windows.

The final `git status` should list `CLAUDE.md` and `TUTORIAL.md` as untracked, and possibly a modified `.vscode/settings.json` (a VS Code setting; leave it). Your old files are safe in the stash.

The stash takes *every* change under `nba-props`, including edits to files git already tracks, such as `nba-props/.gitignore`. To bring one of those back onto your new branch, check it out of the stash:

```powershell
git checkout 'stash@{0}' -- nba-props/.gitignore
``` `git stash list` shows it, and `git stash show --include-untracked --name-only 'stash@{0}'` lists the files in it. Keep the single quotes, because PowerShell treats `@{` specially.

### Step 0.2: Build and run the skeleton

```powershell
cd nba-props
.\mvnw.cmd clean test
```

Expect `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`. The first run downloads dependencies, so it's slow.

`clean` matters here. It deletes the `target/` folder, which still holds compiled classes from the old experiments.

You'll also see warnings such as `Mockito is currently self-attaching...` and `A Java agent has been loaded dynamically`. They're harmless; see [Appendix C](#appendix-c-troubleshooting).

Now start the app:

```powershell
.\mvnw.cmd spring-boot:run
```

Look for these two log lines:

```text
Tomcat started on port 8080 (http) with context path '/'
Started NbaPropsApplication in 1.861 seconds (process running for 2.28)
```

Open http://localhost:8080 in a browser. You'll get a **Whitelabel Error Page** with status 404. That's correct: the app is running, but it has no endpoints yet. Stop it with `Ctrl+C`.

### Step 0.3: Tour the project

| File | What it's for |
|---|---|
| `nba-props/pom.xml` | The Maven build file: Boot version, Java version, dependencies, plugins |
| `nba-props/mvnw`, `mvnw.cmd`, `.mvn/` | The Maven wrapper, which downloads and runs the exact Maven version this project uses |
| `src/main/java/com/example/nbaprops/NbaPropsApplication.java` | The entry point, with `main()` |
| `src/main/resources/application.properties` | Configuration; only the app name so far |
| `src/test/java/com/example/nbaprops/NbaPropsApplicationTests.java` | One test: does the app start? |

Open `pom.xml` and find these parts:

- **`<parent>` is `spring-boot-starter-parent` 4.1.1.** It brings *dependency management*: a curated list of versions for Spring and hundreds of other libraries, all tested together. That's why your dependencies have no `<version>` tags.
- **`<java.version>21</java.version>`** sets the Java version the compiler targets.
- **`spring-boot-starter-webmvc`** is a *starter*: one dependency that pulls in everything a web app needs. That's Spring MVC, an embedded Tomcat server, and Jackson for JSON.
- **`spring-boot-starter-webmvc-test`** brings the test libraries: JUnit, AssertJ, Mockito, and MockMvc.
- **`spring-boot-maven-plugin`** provides `spring-boot:run`, and packages the app as a single runnable jar.

Now open `NbaPropsApplication.java`:

```java
@SpringBootApplication
public class NbaPropsApplication {

	public static void main(String[] args) {
		SpringApplication.run(NbaPropsApplication.class, args);
	}

}
```

`@SpringBootApplication` combines three annotations:

- **`@SpringBootConfiguration`** marks this class as a source of configuration. It's a specialized `@Configuration`.
- **`@EnableAutoConfiguration`** lets Boot configure things for you based on what's on the classpath.
- **`@ComponentScan`** finds your classes annotated with `@Component`, `@Service`, `@RestController`, and so on. It searches this package (`com.example.nbaprops`) and every package below it.

`SpringApplication.run` does three things:
1. Creates the **application context**, Spring's container for the objects it manages. Those objects are called **beans**.
2. Runs auto-configuration.
3. Starts the embedded Tomcat server.

**Auto-configuration** is the heart of Spring Boot. Boot looks at the classpath and your properties, then configures beans to match:
- Tomcat and Spring MVC are on the classpath, so it creates a web server and a `DispatcherServlet`.
- Jackson is there, so it sets up JSON conversion.

You can watch it decide. Start the app with `--debug`, and it prints a `CONDITIONS EVALUATION REPORT` listing which auto-configurations matched and why:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--debug"
```

Keep the quotes. Without them, PowerShell splits arguments like `-Dspring-boot.run.arguments=...` at the dot, and Maven gets garbage.

### Quick checks

**1. Why don't the dependencies in `pom.xml` have version numbers?**

<details><summary>Answer</summary>

The parent (`spring-boot-starter-parent`) imports `spring-boot-dependencies`, a *bill of materials* (BOM) that pins a version for every library Boot supports. You choose the Boot version once, and every library comes in a version known to work with it. You can still override a single library when you need to.

</details>

**2. What is a "starter"?**

<details><summary>Answer</summary>

A dependency with no code of its own, just a curated set of other dependencies for one job. `spring-boot-starter-webmvc` means Spring MVC, embedded Tomcat, Jackson, and logging, in versions that work together.

</details>

**3. `@SpringBootApplication` combines three annotations. What does each one do?**

<details><summary>Answer</summary>

- `@SpringBootConfiguration`: this class is a configuration class.
- `@EnableAutoConfiguration`: configure beans automatically based on the classpath and properties.
- `@ComponentScan`: find annotated classes in this package and its subpackages and register them as beans.

</details>

**4. Why use `mvnw.cmd` instead of installing Maven yourself?**

<details><summary>Answer</summary>

The wrapper pins the Maven version (in `.mvn/wrapper/maven-wrapper.properties`) and downloads it on first use. Everyone who builds the project, including a CI server, uses the same Maven version and needs only a JDK.

</details>

**5. The `contextLoads()` test has an empty body. What does it actually test?**

<details><summary>Answer</summary>

That the application context starts: every bean can be created and wired together, and the configuration is valid. If any bean fails to start, the test fails. It's a cheap smoke test.

</details>

**6. You never installed Tomcat. Where does the web server come from?**

<details><summary>Answer</summary>

Tomcat is *embedded*. It's a library inside your app, pulled in by the webmvc starter, and Boot starts it inside your own JVM. That's why the app runs as a plain Java process (`java -jar`) with no separate server to deploy to.

</details>

### Checkpoint

There's nothing to commit yet, because you haven't changed any files. `git log --oneline -1` should show `1c971e9 git initInitialize a new Git repository`, the commit your `tutorial` branch started from.

## Chapter 1: Your first endpoint

**Goal:** Return a list of players as JSON from `GET /api/players`.

**You'll learn:** `@RestController`, request mappings, how a request reaches your method (the `DispatcherServlet`), how Java objects become JSON (Jackson), and how to test a controller with `@WebMvcTest` and `MockMvc`.

### Concept primer

When a request comes in, this is what happens:

```text
curl.exe http://localhost:8080/api/players
     |
     v
Embedded Tomcat (port 8080)
     |
     v
DispatcherServlet ---- "which method handles GET /api/players?"
     |
     v
PlayerController.getPlayers()  --> returns a List<Player>
     |
     v
Jackson (an HttpMessageConverter) --> [{"id":1,"name":"Stephen Curry",...}]
```

- **`DispatcherServlet`** is Spring MVC's *front controller*. One servlet receives every request and routes it to the handler method whose mapping matches the HTTP method and path.
- **`@RestController`** is `@Controller` plus `@ResponseBody`. Whatever your method returns is written straight into the response body. With plain `@Controller`, a returned `String` is treated as the name of an HTML template to render.
- **Jackson** turns your return value into JSON. You don't configure anything: the webmvc starter put Jackson on the classpath, and auto-configuration registered it. Records work out of the box, with each record component becoming a JSON field.

You'll organize code **by feature**. Everything about players goes in `com.example.nbaprops.player`. Component scanning starts at `NbaPropsApplication`'s package, so every subpackage is found automatically.

### Step 1.1: Create the `Player` record

**Task:** In a new package `com.example.nbaprops.player`, create a record `Player` with three components: `Long id`, `String name`, `String team`.

<details><summary>Hint</summary>

A record is declared as `public record Name(Type a, Type b) { }`. Java generates the constructor, the accessors (`name()`, not `getName()`), and `equals`, `hashCode`, and `toString`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/Player.java`

```java
package com.example.nbaprops.player;

public record Player(Long id, String name, String team) {
}
```

</details>

Why a record? It's an immutable data carrier with almost no boilerplate. (Chapter 4 turns `Player` into a database entity, which has to be a regular class. You'll see why then.)

### Step 1.2: Create the controller

**Task:** In the same package, create `PlayerController`. It should handle `GET /api/players` and return a hardcoded list of two players.

<details><summary>Hint 1</summary>

Put `@RestController` on the class. Put `@RequestMapping("/api/players")` on the class to set the base path for every method in it.

</details>

<details><summary>Hint 2</summary>

A method annotated with `@GetMapping` and no path handles `GET` on the base path. `List.of(...)` builds an immutable list.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerController.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @GetMapping
    public List<Player> getPlayers() {
        return List.of(
                new Player(1L, "Stephen Curry", "Warriors"),
                new Player(2L, "Nikola Jokic", "Nuggets"));
    }
}
```

</details>

### Step 1.3: Run it

```powershell
.\mvnw.cmd spring-boot:run
```

In a second PowerShell terminal:

```powershell
curl.exe -i http://localhost:8080/api/players
```

`-i` prints the status line and headers too. Expected:

```text
HTTP/1.1 200
Content-Type: application/json
...

[{"id":1,"name":"Stephen Curry","team":"Warriors"},{"id":2,"name":"Nikola Jokic","team":"Nuggets"}]
```

Look at the app's log. On the first request you'll see `Initializing Servlet 'dispatcherServlet'`: the front controller starts lazily. Stop the app with `Ctrl+C`.

### Step 1.4: Test the controller

**Task:** Create `PlayerControllerTests` in `src/test/java/com/example/nbaprops/player/`. It should perform `GET /api/players` and assert three things: the status is 200, there are two elements, and the first player's name is `Stephen Curry`.

<details><summary>Hint 1</summary>

Annotate the class with `@WebMvcTest(PlayerController.class)`, and inject `MockMvc` with `@Autowired`.

</details>

<details><summary>Hint 2</summary>

`mockMvc.perform(get("/api/players")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("..."))`. Here `get` comes from `MockMvcRequestBuilders`, and `status` and `jsonPath` come from `MockMvcResultMatchers` (static imports). In JSONPath, `$` is the root, `$[0].name` is the first element's `name`, and `$.length()` is the array's length.

</details>

> **Boot 4 note:** `@WebMvcTest` now lives in `org.springframework.boot.webmvc.test.autoconfigure`. Boot 3 examples import it from `org.springframework.boot.test.autoconfigure.web.servlet`, which no longer exists.

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerControllerTests.java`

```java
package com.example.nbaprops.player;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPlayersReturnsAJsonArray() throws Exception {
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"))
                .andExpect(jsonPath("$[0].team").value("Warriors"));
    }
}
```

</details>

Run only this test class:

```powershell
.\mvnw.cmd test -Dtest=PlayerControllerTests
```

Expected: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

`MockMvc` doesn't start a real server. It calls the `DispatcherServlet` directly with mock request and response objects, so it's fast, and it still exercises your mappings and JSON conversion.

### Quick checks

**1. What's the difference between `@Controller` and `@RestController`?**

<details><summary>Answer</summary>

`@RestController` is `@Controller` plus `@ResponseBody`. Return values are written into the HTTP response body (as JSON here). With a plain `@Controller`, return values are treated as view names (HTML templates) unless the method has `@ResponseBody`.

</details>

**2. Your method returns a `List<Player>`. Who turns it into JSON, and why didn't you configure anything?**

<details><summary>Answer</summary>

An `HttpMessageConverter` backed by Jackson. The webmvc starter put Jackson on the classpath, and auto-configuration registered the converter and a JSON mapper. Spring picks the converter based on the return type and the client's `Accept` header.

</details>

**3. What does `@WebMvcTest(PlayerController.class)` load, and what does it leave out?**

<details><summary>Answer</summary>

It loads only the web layer: the controller you named, Spring MVC's infrastructure, JSON conversion, and things like `@ControllerAdvice` classes and filters. It doesn't load services, repositories, or a database. That makes it a fast, focused *slice* test. `@SpringBootTest` loads the entire application.

</details>

**4. What happens if you move `PlayerController` to a package called `com.example.other`?**

<details><summary>Answer</summary>

Component scanning starts at `com.example.nbaprops`, so it never finds the controller. It doesn't become a bean, nothing maps `/api/players`, and requests get a 404.

</details>

**5. Does `MockMvc` start Tomcat?**

<details><summary>Answer</summary>

No. It drives the `DispatcherServlet` in memory with mock request and response objects. There's no network and no port. (Chapter 7 shows a test that does start a real server.)

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 1: first endpoint"
```

Run this from `nba-props`, where `.` means "everything in this folder".

## Chapter 2: Beans and dependency injection

**Goal:** Split the code into layers (controller, service, repository) and let Spring create and connect those objects.

**You'll learn:** Inversion of Control, the application context, beans and stereotype annotations, constructor injection, singleton scope and thread safety, and replacing a dependency with a mock in a slice test (`@MockitoBean`).

### Concept primer

Right now the controller does everything. Real applications split responsibilities into layers:

| Layer | Its job | Talks to |
|---|---|---|
| Controller | HTTP: paths, status codes, JSON | the service |
| Service | Business rules ("player names must be unique") and transactions | the repository |
| Repository | Storing and loading data | the database |

Each layer only talks to the one below it. That keeps HTTP details out of your business logic, and database details out of your controllers.

**Who creates these objects?** You could write `new PlayerController(new PlayerService(new PlayerRepository()))` yourself. In Spring, you don't:

1. You mark classes as *components*.
2. The **application context** (the *IoC container*) creates one instance of each. That instance is a **bean**.
3. The container hands each bean the other beans it needs.

This is **Inversion of Control** (IoC): your classes declare what they need, and the container controls how it gets created. Handing a bean its dependencies is called **dependency injection** (DI).

```text
Application context (created by SpringApplication.run)
+--------------------------------------------------------------------+
|  playerRepository  <--needs--  playerService  <--needs--  playerController
|  (@Repository)                 (@Service)                 (@RestController)
+--------------------------------------------------------------------+
```

Component scanning finds classes with these annotations:

| Annotation | Meaning |
|---|---|
| `@Component` | A generic Spring-managed bean |
| `@Service` | A `@Component` that holds business logic |
| `@Repository` | A `@Component` for data access. Spring also translates database exceptions into its own `DataAccessException` types |
| `@Controller` / `@RestController` | A `@Component` that handles web requests |

**Constructor injection:** if a bean's class has exactly one constructor, Spring calls it and passes in beans matching the parameter types. You don't need `@Autowired`.

### Step 2.1: Move the data into a repository

**Task:** Create `PlayerRepository` in the `player` package and annotate it with `@Repository`. It keeps players in memory in a `Map<Long, Player>` and has:

- a constructor that saves the two players (Curry and Jokic);
- `List<Player> findAll()`, sorted by id;
- `Player save(Player player)`, which assigns the next id when `player.id()` is `null`.

Name the methods exactly `findAll` and `save`. In chapter 4 you'll replace this class with Spring Data JPA, whose repositories have methods with these same names, so the service won't have to change.

<details><summary>Hint 1</summary>

A bean is a singleton shared by every request, and Tomcat handles requests on many threads at once. Use a `ConcurrentHashMap` for the players and an `AtomicLong` for the next id.

</details>

<details><summary>Hint 2</summary>

Records are immutable. To "set" the id, create a new `Player` with the id and the other two fields copied over.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerRepository.java`

```java
package com.example.nbaprops.player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

@Repository
public class PlayerRepository {

    private final Map<Long, Player> players = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public PlayerRepository() {
        save(new Player(null, "Stephen Curry", "Warriors"));
        save(new Player(null, "Nikola Jokic", "Nuggets"));
    }

    public List<Player> findAll() {
        return players.values().stream()
                .sorted(Comparator.comparing(Player::id))
                .toList();
    }

    public Player save(Player player) {
        Long id = player.id() != null ? player.id() : nextId.getAndIncrement();
        Player saved = new Player(id, player.name(), player.team());
        players.put(id, saved);
        return saved;
    }
}
```

</details>

### Step 2.2: Add a service and inject it

**Task:**

1. Create `PlayerService`, annotated with `@Service`. Its constructor takes a `PlayerRepository` and stores it in a `private final` field. Its `findAll()` method returns `playerRepository.findAll()`.
2. Change `PlayerController` so its constructor takes a `PlayerService`, and `getPlayers()` returns `playerService.findAll()` instead of the hardcoded list.

<details><summary>Hint</summary>

You don't need `@Autowired` anywhere: each class has a single constructor, so Spring uses it.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerService.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<Player> findAll() {
        return playerRepository.findAll();
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerController.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public List<Player> getPlayers() {
        return playerService.findAll();
    }
}
```

</details>

For now the service is a pass-through, but it's where business rules go, and it fills up quickly: filtering in chapter 3, duplicate checks in chapter 5, transactions in chapter 6.

Start the app and run `curl.exe -i http://localhost:8080/api/players`. You get the same JSON as before: the behavior didn't change, only the structure.

> **Try this:** Comment out `@Service` on `PlayerService` and start the app. It fails right away:
>
> ```text
> ***************************
> APPLICATION FAILED TO START
> ***************************
>
> Description:
>
> Parameter 0 of constructor in com.example.nbaprops.player.PlayerController required a bean of type 'com.example.nbaprops.player.PlayerService' that could not be found.
> ```
>
> Without the annotation, component scanning skips the class, so there's no bean to inject. That's exactly why the `PlayerService` in your earlier experiment was never used. Put `@Service` back.

### Step 2.3: Unit-test the repository and service without Spring

**Task:** Write two plain JUnit tests, with no Spring annotations at all:

- `PlayerRepositoryTests`: a new repository starts with Curry and Jokic; `save` gives a new player id `3`, and `findAll()` then contains it.
- `PlayerServiceTests`: `findAll()` returns the repository's players.

<details><summary>Hint 1</summary>

Thanks to constructor injection, you can build the objects yourself: `new PlayerService(new PlayerRepository())`.

</details>

<details><summary>Hint 2</summary>

With AssertJ: `assertThat(list).extracting(Player::name).containsExactly("Stephen Curry", "Nikola Jokic")`. It's `Player::name`, because record accessors have no `get` prefix. Your earlier experiment's test used `Player::Name`, which doesn't compile.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerRepositoryTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerRepositoryTests {

    private final PlayerRepository playerRepository = new PlayerRepository();

    @Test
    void startsWithTwoSeededPlayers() {
        assertThat(playerRepository.findAll())
                .extracting(Player::name)
                .containsExactly("Stephen Curry", "Nikola Jokic");
    }

    @Test
    void saveAssignsTheNextId() {
        Player saved = playerRepository.save(new Player(null, "Shai Gilgeous-Alexander", "Thunder"));

        assertThat(saved.id()).isEqualTo(3L);
        assertThat(playerRepository.findAll()).contains(saved);
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerServiceTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerServiceTests {

    private final PlayerService playerService = new PlayerService(new PlayerRepository());

    @Test
    void findAllReturnsEveryPlayerFromTheRepository() {
        assertThat(playerService.findAll())
                .extracting(Player::name)
                .containsExactly("Stephen Curry", "Nikola Jokic");
    }
}
```

</details>

Run both classes. The quotes are required: without them, PowerShell reads the comma as an array operator and reports a parse error.

```powershell
.\mvnw.cmd test "-Dtest=PlayerRepositoryTests,PlayerServiceTests"
```

Expected: `Tests run: 3, Failures: 0`. These tests run in milliseconds, because no Spring context starts at all.

### Step 2.4: Mock the service in the controller test

Run `.\mvnw.cmd test -Dtest=PlayerControllerTests` now. It fails with:

```text
NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.nbaprops.player.PlayerService' available: expected at least 1 bean which qualifies as autowire candidate.
```

`@WebMvcTest` only creates web-layer beans. `PlayerController` needs a `PlayerService`, and the slice didn't create one.

**Task:** Add a field `@MockitoBean private PlayerService playerService;` to the test. In the test method, tell the mock what to return using `when(playerService.findAll()).thenReturn(List.of(...))`, then assert on the JSON as before.

> **Boot 4 note:** `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`) replaces `@MockBean`, which was removed in Boot 4. Most Spring tutorials online still use `@MockBean`, and it won't compile here.

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerControllerTests.java`

```java
package com.example.nbaprops.player;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void getPlayersReturnsWhatTheServiceReturns() throws Exception {
        when(playerService.findAll()).thenReturn(List.of(
                new Player(1L, "Stephen Curry", "Warriors"),
                new Player(2L, "Nikola Jokic", "Nuggets")));

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"))
                .andExpect(jsonPath("$[1].team").value("Nuggets"));
    }
}
```

</details>

Now run the whole suite:

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`.

Why use a mock here? A controller test should test only the controller: routing, status codes, and JSON. A mock lets you decide exactly what the service returns, so a bug in the repository can't make the controller test fail.

### Quick checks

**1. What is Inversion of Control, and what is the "container"?**

<details><summary>Answer</summary>

Normally, your code creates the objects it depends on with `new`. With IoC, a container creates and connects them, and your classes only declare what they need (constructor parameters). In Spring the container is the `ApplicationContext`, and the objects it manages are called beans.

</details>

**2. Why is constructor injection preferred over putting `@Autowired` on a field?**

<details><summary>Answer</summary>

- Dependencies can be `final`: set once, never `null` after construction.
- They're visible in the constructor. A long parameter list tells you the class does too much.
- You can create the object in a plain unit test with `new`, as you did in Step 2.3. Field injection needs reflection (from Spring or Mockito) to set private fields.

</details>

**3. What's the difference between `@Component`, `@Service`, and `@Repository`?**

<details><summary>Answer</summary>

All three make a class a bean that component scanning will find. `@Service` and `@Repository` are specializations that document which layer the class belongs to. `@Repository` also enables exception translation: database-specific exceptions are converted into Spring's `DataAccessException` hierarchy.

</details>

**4. Beans are singletons by default. What does that mean for `PlayerRepository`?**

<details><summary>Answer</summary>

The container creates one instance and shares it with every caller, including requests running at the same time on different threads. So the repository must be thread-safe (hence `ConcurrentHashMap` and `AtomicLong`). In general, services and repositories shouldn't store per-request data in fields.

</details>

**5. Why did the `@WebMvcTest` fail until you added `@MockitoBean`?**

<details><summary>Answer</summary>

A `@WebMvcTest` slice only creates web-layer beans (controllers, advice, filters). `PlayerController` needs a `PlayerService` bean, which didn't exist, so the context couldn't start. `@MockitoBean` registers a Mockito mock of that type as a bean.

</details>

**6. Suppose two beans implement the same interface and a constructor asks for that interface. Which one gets injected?**

<details><summary>Answer</summary>

Unless something breaks the tie, neither: startup fails with `NoUniqueBeanDefinitionException`. Spring tries these tie-breakers in order:

1. A bean marked `@Primary` is the default choice.
2. `@Qualifier("beanName")` on the constructor parameter picks one explicitly.
3. As a last resort, a parameter whose *name* matches one of the bean names picks that bean.

Prefer the first two: renaming a parameter shouldn't change which bean you get.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 2: beans and dependency injection"
```

## Chapter 3: Designing a REST API

**Goal:** Turn the player list into a small REST resource. Clients can list players with a filter, fetch one, and create new ones, and every response has the right status code.

**You'll learn:** resources and HTTP methods, status codes, `@PathVariable`, `@RequestParam`, `@RequestBody`, `ResponseEntity`, `201 Created` with a `Location` header, and request/response DTOs.

### Concept primer

REST treats the things your API manages as **resources**. Each resource is identified by a URL, and you act on it with standard HTTP methods:

| Request | Meaning | Status on success |
|---|---|---|
| `GET /api/players` | List the collection | 200 OK |
| `GET /api/players/7` | Get one player | 200 OK (or 404 Not Found) |
| `POST /api/players` | Create a player | 201 Created, plus `Location: /api/players/8` |
| `PUT /api/players/7` | Replace a player | 200 OK |
| `DELETE /api/players/7` | Delete a player | 204 No Content |

Two properties matter for these methods:

- A method is **safe** if it never changes anything on the server. GET is safe.
- A method is **idempotent** if sending the same request twice has the same effect as sending it once. GET, PUT, and DELETE are idempotent. POST is neither: two identical POSTs create two players.

Spring binds request data to your method parameters with these annotations:

| Annotation | Reads from | Example |
|---|---|---|
| `@PathVariable` | A segment of the URL | `/api/players/{id}` |
| `@RequestParam` | The query string | `/api/players?team=Warriors` |
| `@RequestBody` | The request body (JSON) | `{"name": "Luka Doncic", "team": "Lakers"}` |

**DTOs (data transfer objects).** The JSON your API accepts and returns is a contract with its clients. If controllers return internal objects directly, every internal change leaks into that contract: a new field, a rename, or (in chapter 4) a switch to a database entity. So you'll define separate records:

- `CreatePlayerRequest` for input. It has no `id`, because the server assigns ids.
- `PlayerResponse` for output.

The service converts between these and the internal `Player`.

### Step 3.1: Create the DTOs

**Task:** In the `player` package, create two records:

- `CreatePlayerRequest(String name, String team)`
- `PlayerResponse(Long id, String name, String team)`

Give `PlayerResponse` a static factory method, `from(Player player)`.

<details><summary>Hint</summary>

Records can have static methods: `public static PlayerResponse from(Player player) { return new PlayerResponse(...); }`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/CreatePlayerRequest.java`

```java
package com.example.nbaprops.player;

public record CreatePlayerRequest(String name, String team) {
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerResponse.java`

```java
package com.example.nbaprops.player;

public record PlayerResponse(Long id, String name, String team) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.id(), player.name(), player.team());
    }
}
```

</details>

### Step 3.2: Extend the repository and the service

**Task:**

- In the repository, add `Optional<Player> findById(Long id)` and `List<Player> findByTeamIgnoreCase(String team)`. Again, these names match what Spring Data will generate for you in chapter 4.
- Change the service to work with DTOs:
  - `List<PlayerResponse> findAll(String team)` returns every player when `team` is `null` or blank. Otherwise it returns only that team's players, ignoring case.
  - `Optional<PlayerResponse> findById(Long id)`
  - `PlayerResponse create(CreatePlayerRequest request)`

<details><summary>Hint 1</summary>

`optional.map(PlayerResponse::from)` converts the value inside an `Optional`. `list.stream().map(PlayerResponse::from).toList()` converts a whole list.

</details>

<details><summary>Hint 2</summary>

Returning `Optional` makes "might not exist" explicit in the method signature. The controller then decides what "not found" means in HTTP terms.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerRepository.java`

```java
package com.example.nbaprops.player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

@Repository
public class PlayerRepository {

    private final Map<Long, Player> players = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public PlayerRepository() {
        save(new Player(null, "Stephen Curry", "Warriors"));
        save(new Player(null, "Nikola Jokic", "Nuggets"));
    }

    public List<Player> findAll() {
        return players.values().stream()
                .sorted(Comparator.comparing(Player::id))
                .toList();
    }

    public Optional<Player> findById(Long id) {
        return Optional.ofNullable(players.get(id));
    }

    public List<Player> findByTeamIgnoreCase(String team) {
        return findAll().stream()
                .filter(player -> team.equalsIgnoreCase(player.team()))
                .toList();
    }

    public Player save(Player player) {
        Long id = player.id() != null ? player.id() : nextId.getAndIncrement();
        Player saved = new Player(id, player.name(), player.team());
        players.put(id, saved);
        return saved;
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerService.java`

```java
package com.example.nbaprops.player;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<PlayerResponse> findAll(String team) {
        List<Player> players = (team == null || team.isBlank())
                ? playerRepository.findAll()
                : playerRepository.findByTeamIgnoreCase(team);
        return players.stream()
                .map(PlayerResponse::from)
                .toList();
    }

    public Optional<PlayerResponse> findById(Long id) {
        return playerRepository.findById(id)
                .map(PlayerResponse::from);
    }

    public PlayerResponse create(CreatePlayerRequest request) {
        Player saved = playerRepository.save(new Player(null, request.name(), request.team()));
        return PlayerResponse.from(saved);
    }
}
```

</details>

### Step 3.3: Add the endpoints

**Task:** Update `PlayerController` so that:

- `GET /api/players` takes an optional `team` query parameter.
- `GET /api/players/{id}` returns 200 with the player, or 404 with an empty body.
- `POST /api/players` accepts a `CreatePlayerRequest` as JSON and returns 201 with the new player as the body and a `Location` header pointing to it.

<details><summary>Hint 1</summary>

Use `@RequestParam(required = false) String team`. Without `required = false`, a request with no `?team=` is rejected with 400 Bad Request.

</details>

<details><summary>Hint 2</summary>

`ResponseEntity` lets you choose the status and headers: `ResponseEntity.ok(body)`, `ResponseEntity.notFound().build()`, `ResponseEntity.created(uri).body(body)`. (There's also a shortcut, `ResponseEntity.of(optional)`.)

</details>

<details><summary>Hint 3</summary>

Build the `Location` URI from the current request: `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerController.java`

```java
package com.example.nbaprops.player;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public List<PlayerResponse> getPlayers(@RequestParam(required = false) String team) {
        return playerService.findAll(team);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> getPlayer(@PathVariable Long id) {
        return playerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(@RequestBody CreatePlayerRequest request) {
        PlayerResponse created = playerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
```

</details>

### Step 3.4: Try it

Start the app. In a second terminal, create a player. `Invoke-RestMethod` is PowerShell's built-in HTTP client, and it turns the JSON response into an object:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/players -ContentType 'application/json' -Body '{"name": "Luka Doncic", "team": "Lakers"}'
```

```text
id name        team
-- ----        ----
 3 Luka Doncic Lakers
```

Use single quotes around the JSON. PowerShell treats single-quoted strings literally, so the double quotes inside reach the server unchanged.

To see the status code and headers, use `Invoke-WebRequest` instead. In Windows PowerShell 5.1, add `-UseBasicParsing` so it doesn't depend on Internet Explorer:

```powershell
$response = Invoke-WebRequest -Method Post -Uri http://localhost:8080/api/players -ContentType 'application/json' -Body '{"name": "Anthony Edwards", "team": "Timberwolves"}' -UseBasicParsing
$response.StatusCode
$response.Headers.Location
```

```text
201
http://localhost:8080/api/players/4
```

Now try some GETs:

```powershell
curl.exe -i http://localhost:8080/api/players/3
curl.exe -i http://localhost:8080/api/players/99
curl.exe -i "http://localhost:8080/api/players?team=warriors"
```

Expected:
1. 200 with Luka.
2. 404 with an empty body (`Content-Length: 0`).
3. 200 with only Stephen Curry. The filter ignores case.

Restart the app and list the players again. Luka and Anthony are gone, because the data only lives in a map inside the running app. Chapter 4 moves it into a real database (still in memory at first), and chapter 8 makes it survive restarts with PostgreSQL.

### Step 3.5: Test the new behavior

**Task:**

- `PlayerServiceTests`:
  - `findAll(null)` returns both players.
  - `findAll("warriors")` returns only Curry.
  - `findById(99L)` is empty.
  - `create` assigns an id, and the new player can be found afterwards.
- `PlayerControllerTests`: stub the service, then check each endpoint:
  - the list
  - the list with `?team=`
  - get by id (200)
  - get by id (404)
  - create: 201, the `Location` header `http://localhost/api/players/3`, and the body

<details><summary>Hint 1</summary>

The MockMvc building blocks:
- Query parameter: `get("/api/players").param("team", "Warriors")`
- JSON body: `post("/api/players").contentType(MediaType.APPLICATION_JSON).content("""...""")`, using a text block for the JSON
- Assertions: `header().string("Location", "...")`, `status().isCreated()`, `status().isNotFound()`

</details>

<details><summary>Hint 2</summary>

MockMvc requests go to `http://localhost` with no port, so that's the prefix of the `Location` header.

</details>

<details><summary>Hint 3</summary>

`when(playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers")))` works because records implement `equals()`. The request object Jackson builds from your JSON is equal to the one in the stub.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerServiceTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerServiceTests {

    private final PlayerService playerService = new PlayerService(new PlayerRepository());

    @Test
    void findAllWithoutATeamReturnsEveryPlayer() {
        assertThat(playerService.findAll(null))
                .extracting(PlayerResponse::name)
                .containsExactly("Stephen Curry", "Nikola Jokic");
    }

    @Test
    void findAllWithATeamIgnoresCase() {
        assertThat(playerService.findAll("warriors"))
                .extracting(PlayerResponse::name)
                .containsExactly("Stephen Curry");
    }

    @Test
    void findByIdIsEmptyForAnUnknownId() {
        assertThat(playerService.findById(99L)).isEmpty();
    }

    @Test
    void createAssignsAnIdAndStoresThePlayer() {
        PlayerResponse created = playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers"));

        assertThat(created.id()).isNotNull();
        assertThat(playerService.findById(created.id())).contains(created);
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerControllerTests.java`

```java
package com.example.nbaprops.player;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void getPlayersReturnsWhatTheServiceReturns() throws Exception {
        when(playerService.findAll(null)).thenReturn(List.of(
                new PlayerResponse(1L, "Stephen Curry", "Warriors"),
                new PlayerResponse(2L, "Nikola Jokic", "Nuggets")));

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"))
                .andExpect(jsonPath("$[1].team").value("Nuggets"));
    }

    @Test
    void getPlayersPassesTheTeamFilterToTheService() throws Exception {
        when(playerService.findAll("Warriors")).thenReturn(List.of(
                new PlayerResponse(1L, "Stephen Curry", "Warriors")));

        mockMvc.perform(get("/api/players").param("team", "Warriors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"));
    }

    @Test
    void getPlayerReturns200WhenFound() throws Exception {
        when(playerService.findById(1L))
                .thenReturn(Optional.of(new PlayerResponse(1L, "Stephen Curry", "Warriors")));

        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stephen Curry"));
    }

    @Test
    void getPlayerReturns404WhenMissing() throws Exception {
        when(playerService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/players/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPlayerReturns201WithALocationHeader() throws Exception {
        when(playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers")))
                .thenReturn(new PlayerResponse(3L, "Luka Doncic", "Lakers"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Luka Doncic", "team": "Lakers"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/players/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Luka Doncic"));
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 12, Failures: 0, Errors: 0`.

### Quick checks

**1. Which HTTP methods are safe, and which are idempotent? Why does it matter?**

<details><summary>Answer</summary>

- Safe (no state change): GET, HEAD, OPTIONS.
- Idempotent (repeating it has the same effect as doing it once): GET, PUT, DELETE.
- POST is neither.

It matters for retries. A client, proxy, or library can safely repeat an idempotent request after a timeout, but retrying a POST can create duplicates.

</details>

**2. Why should `POST /api/players` return 201 with a `Location` header instead of 200?**

<details><summary>Answer</summary>

201 Created says a new resource was created, and `Location` tells the client its URL, so the client can fetch it later without guessing. This is the HTTP convention that clients and tools expect.

</details>

**3. When do you use `@PathVariable`, `@RequestParam`, and `@RequestBody`?**

<details><summary>Answer</summary>

- Path variables identify a resource (`/players/7`).
- Request parameters are optional modifiers, such as filtering, sorting, and paging (`?team=Warriors`).
- The request body carries the data you're creating or replacing (JSON in a POST or PUT).

</details>

**4. Why return `PlayerResponse` instead of `Player`?**

<details><summary>Answer</summary>

The API is a contract. DTOs let you control exactly which fields are exposed and which are accepted (clients can't send an `id`). You can then change internal classes without breaking clients. You'll see this pay off in chapter 4, when `Player` becomes a JPA entity and the controller doesn't change at all.

</details>

**5. What does `ResponseEntity` give you that returning a `PlayerResponse` directly doesn't?**

<details><summary>Answer</summary>

Control over the status code and the headers. If you return an object directly, the status is always 200, unless an exception is thrown or you add `@ResponseStatus`.

</details>

**6. What happens if a client POSTs JSON without a `Content-Type: application/json` header?**

<details><summary>Answer</summary>

Spring can't find a message converter for that content type, so it responds with `415 Unsupported Media Type`. Try it: leave out `-ContentType` in the `Invoke-WebRequest` command, and PowerShell sends `application/x-www-form-urlencoded` instead.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 3: designing a REST API"
```

## Chapter 4: Persistence with Spring Data JPA and H2

**Goal:** Store players in a real database instead of a map, without changing the controller.

**You'll learn:**
- ORM, JPA, and Hibernate
- entities, and why a record can't be one
- Spring Data repositories and derived queries
- `ddl-auto`
- seeding data with a `CommandLineRunner`
- the H2 console
- testing repositories with `@DataJpaTest` and services with Mockito

### Concept primer

- **JPA (Jakarta Persistence)** is the standard Java API for *object-relational mapping* (ORM): mapping objects to database tables. You annotate a class with `@Entity`, and the JPA implementation writes the SQL. Boot uses **Hibernate** as the implementation.
- **Spring Data JPA** goes a step further. You write a repository *interface*, and Spring generates the implementation at startup. Extending `JpaRepository<Player, Long>` gives you `save`, `findById`, `findAll`, `deleteById`, `count`, and more for free. You can also declare **derived queries**: Spring reads a method name like `findByTeamIgnoreCase` and builds the query from it.
- **H2** is a small database written in Java that can run in memory, inside your app. It's ideal for development and tests. Its data disappears when the app stops.

```text
PlayerService
     |  playerRepository.findByTeamIgnoreCase("warriors")
     v
PlayerRepository   (an interface; Spring generates the class)
     |  Hibernate generates: select ... from player where upper(team)=upper(?)
     v
H2 database (in memory)
```

**Why can't an entity be a record?** JPA needs three things that records don't allow:

1. A no-argument constructor, so it can create an empty object and then fill in its fields. A record only has its canonical constructor.
2. Fields it can change after construction, when loading or updating a row. Record fields are `final`.
3. A class it can subclass, to create *proxies* for lazy loading (chapter 6). Records are `final`.

So entities are regular classes, and records stay in use for DTOs.

### Step 4.1: Add the dependencies

**Task:** Add four dependencies to `pom.xml`:
- `spring-boot-starter-data-jpa`
- `spring-boot-h2console`
- `com.h2database:h2`, with `runtime` scope
- the test starter `spring-boot-starter-data-jpa-test`, with `test` scope

<details><summary>Solution</summary>

**Excerpt:** `nba-props/pom.xml`

```xml
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-h2console</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

</details>

`runtime` scope means H2 is on the classpath when the app runs, but your code can't compile against it. That's a good thing: your code only talks to JPA, never to H2 directly.

> **Boot 4 note:** Boot 4 split things into smaller modules. The H2 web console is its own module (`spring-boot-h2console`), and each technology has its own test starter (`spring-boot-starter-data-jpa-test`). Boot 3 bundled all of this into larger jars, so older tutorials don't mention these dependencies.

If VS Code asks whether to update the Java project configuration after you edit `pom.xml`, choose **Yes**.

### Step 4.2: Configure the database

**Task:** In `application.properties`:

- set the datasource URL to an in-memory H2 database named `nbaprops`;
- let Hibernate create the tables on startup and drop them on shutdown;
- log the SQL Hibernate runs;
- enable the H2 console.

<details><summary>Hint</summary>

The property names are:
- `spring.datasource.url` (the value is `jdbc:h2:mem:nbaprops`)
- `spring.jpa.hibernate.ddl-auto`
- `spring.jpa.show-sql`
- `spring.h2.console.enabled`

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/resources/application.properties`

```properties
spring.application.name=nba-props

# In-memory H2 database. The fixed name lets you connect to it from the H2 console.
spring.datasource.url=jdbc:h2:mem:nbaprops
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.h2.console.enabled=true
```

</details>

`ddl-auto` controls what Hibernate does to the schema at startup:

| Value | What it does |
|---|---|
| `none` | Nothing |
| `validate` | Checks that the tables match your entities, and fails if they don't |
| `update` | Adds missing tables and columns; never drops anything |
| `create` | Drops and recreates everything on startup |
| `create-drop` | Like `create`, and also drops everything on shutdown |

### Step 4.3: Turn `Player` into an entity

**Task:** Replace the `Player` record with a class annotated with `@Entity`. It needs:

- `Long id`, annotated with `@Id` and `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `name`, annotated with `@Column(nullable = false, unique = true)`
- `team`
- a `protected` no-argument constructor, for JPA
- a public constructor `Player(String name, String team)`
- getters

Then fix the two compile errors this causes: `PlayerResponse.from` must call getters (`getName()`), and `PlayerService.create` must use the new constructor.

<details><summary>Hint</summary>

Import the annotations from `jakarta.persistence`, not `javax.persistence`: Spring Boot 3 and later use Jakarta EE. `IDENTITY` lets the database assign ids through an auto-increment column.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/Player.java`

```java
package com.example.nbaprops.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String team;

    protected Player() {
        // required by JPA
    }

    public Player(String name, String team) {
        this.name = name;
        this.team = team;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeam() {
        return team;
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerResponse.java`

```java
package com.example.nbaprops.player;

public record PlayerResponse(Long id, String name, String team) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getName(), player.getTeam());
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerService.java`

```java
package com.example.nbaprops.player;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<PlayerResponse> findAll(String team) {
        List<Player> players = (team == null || team.isBlank())
                ? playerRepository.findAll()
                : playerRepository.findByTeamIgnoreCase(team);
        return players.stream()
                .map(PlayerResponse::from)
                .toList();
    }

    public Optional<PlayerResponse> findById(Long id) {
        return playerRepository.findById(id)
                .map(PlayerResponse::from);
    }

    public PlayerResponse create(CreatePlayerRequest request) {
        Player saved = playerRepository.save(new Player(request.name(), request.team()));
        return PlayerResponse.from(saved);
    }
}
```

</details>

### Step 4.4: Let Spring Data write the repository

**Task:** Replace the `PlayerRepository` class with an interface that extends `JpaRepository<Player, Long>`. It declares one extra method: `List<Player> findByTeamIgnoreCase(String team)`. Delete everything else; Spring generates the implementation.

<details><summary>Hint</summary>

The type parameters are `JpaRepository<EntityType, IdType>`. You don't need any annotation: Spring Data finds interfaces that extend its repository types automatically.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerRepository.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeamIgnoreCase(String team);
}
```

</details>

Look at what didn't change. `PlayerService` still calls `findAll()`, `findById()`, `findByTeamIgnoreCase()`, and `save()`, the names you chose back in chapter 2. `PlayerController` doesn't change at all, because it only deals in DTOs.

### Step 4.5: Seed data at startup

**Task:** Create `com.example.nbaprops.config.DataSeeder`, a `@Component` that implements `CommandLineRunner`. In `run`, if the table is empty, save four players and log how many were seeded:
- Stephen Curry (Warriors)
- Nikola Jokic (Nuggets)
- Shai Gilgeous-Alexander (Thunder)
- Victor Wembanyama (Spurs)

<details><summary>Hint 1</summary>

Spring Boot calls every `CommandLineRunner` bean once, right after the application context has started. Use `playerRepository.count()` and `playerRepository.saveAll(List.of(...))`.

</details>

<details><summary>Hint 2</summary>

To log, use SLF4J, which comes with Boot: `private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);`, then `log.info("Seeded {} players", count)`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/config/DataSeeder.java`

```java
package com.example.nbaprops.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.nbaprops.player.Player;
import com.example.nbaprops.player.PlayerRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PlayerRepository playerRepository;

    public DataSeeder(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public void run(String... args) {
        if (playerRepository.count() > 0) {
            return;
        }
        playerRepository.saveAll(List.of(
                new Player("Stephen Curry", "Warriors"),
                new Player("Nikola Jokic", "Nuggets"),
                new Player("Shai Gilgeous-Alexander", "Thunder"),
                new Player("Victor Wembanyama", "Spurs")));
        log.info("Seeded {} players", playerRepository.count());
    }
}
```

</details>

Why check `count() > 0` first? It makes seeding safe to run more than once. That matters in chapter 8, when the database survives restarts.

### Step 4.6: Run it and look inside the database

Start the app. The log now shows Hibernate's SQL:

```text
Hibernate: create table player (id bigint generated by default as identity, name varchar(255) not null unique, team varchar(255), primary key (id))
...
Hibernate: insert into player (name,team,id) values (?,?,default)
...
INFO ... com.example.nbaprops.config.DataSeeder   : Seeded 4 players
```

You'll also see a warning about `spring.jpa.open-in-view`. Ignore it for now; chapter 6 explains it.

`curl.exe http://localhost:8080/api/players` now returns the four seeded players.

Next, open http://localhost:8080/h2-console in a browser and fill in the login form:
- **JDBC URL:** `jdbc:h2:mem:nbaprops`
- **User Name:** `sa`
- **Password:** leave it empty

Click **Connect**, then run `SELECT * FROM PLAYER;`. Create a player with the `Invoke-RestMethod` command from chapter 3, and run the query again.

### Step 4.7: Test the repository and the service

**Task:**

- Rewrite `PlayerRepositoryTests` as a `@DataJpaTest` with two tests:
  - Save a player and check that it gets an id.
  - Save two players and check that `findByTeamIgnoreCase("warriors")` finds only Curry.
- Rewrite `PlayerServiceTests` with Mockito. The repository is now an interface, so you can't create it with `new`; mock it instead. Cover:
  - `findAll`, with and without a team
  - `findById` for an unknown id
  - `create`, capturing the entity that gets saved

<details><summary>Hint 1</summary>

`@DataJpaTest` starts only the JPA pieces: entities, repositories, and an embedded database. Each test runs in a transaction that's rolled back at the end, so tests never see each other's data. `DataSeeder` doesn't run, because it isn't a JPA component, so every test starts with an empty database.

</details>

<details><summary>Hint 2</summary>

For Mockito without Spring:
- Annotate the class with `@ExtendWith(MockitoExtension.class)`.
- Declare `@Mock PlayerRepository playerRepository`.
- Declare `@InjectMocks PlayerService playerService`. Mockito calls the constructor and passes in the mock.

</details>

<details><summary>Hint 3</summary>

A mocked `save` returns `null` unless you stub it. `when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0))` makes it return whatever was passed in. An `ArgumentCaptor` captures the argument so you can assert on it.

</details>

> **Boot 4 note:** `@DataJpaTest` is in `org.springframework.boot.data.jpa.test.autoconfigure`. In Boot 3 it was in `org.springframework.boot.test.autoconfigure.orm.jpa`.

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerRepositoryTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PlayerRepositoryTests {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void saveAssignsAnId() {
        Player saved = playerRepository.save(new Player("Stephen Curry", "Warriors"));

        assertThat(saved.getId()).isNotNull();
        assertThat(playerRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByTeamIgnoreCaseMatchesRegardlessOfCase() {
        playerRepository.save(new Player("Stephen Curry", "Warriors"));
        playerRepository.save(new Player("Nikola Jokic", "Nuggets"));

        assertThat(playerRepository.findByTeamIgnoreCase("warriors"))
                .extracting(Player::getName)
                .containsExactly("Stephen Curry");
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerServiceTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTests {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void findAllWithoutATeamReturnsEveryPlayer() {
        when(playerRepository.findAll()).thenReturn(List.of(
                new Player("Stephen Curry", "Warriors"),
                new Player("Nikola Jokic", "Nuggets")));

        assertThat(playerService.findAll(null))
                .extracting(PlayerResponse::name)
                .containsExactly("Stephen Curry", "Nikola Jokic");
    }

    @Test
    void findAllWithATeamUsesTheTeamQuery() {
        when(playerRepository.findByTeamIgnoreCase("warriors"))
                .thenReturn(List.of(new Player("Stephen Curry", "Warriors")));

        assertThat(playerService.findAll("warriors"))
                .extracting(PlayerResponse::name)
                .containsExactly("Stephen Curry");
    }

    @Test
    void findByIdIsEmptyForAnUnknownId() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(playerService.findById(99L)).isEmpty();
    }

    @Test
    void createSavesANewPlayer() {
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerResponse created = playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers"));

        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Luka Doncic");
        assertThat(created.team()).isEqualTo("Lakers");
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 12, Failures: 0, Errors: 0`. `PlayerControllerTests` passes without a single change.

### Quick checks

**1. How are JPA, Hibernate, and Spring Data JPA related?**

<details><summary>Answer</summary>

JPA is the specification: the annotations and the `EntityManager` API. Hibernate is an implementation of it. Spring Data JPA sits on top: it generates repository implementations from your interfaces, so you rarely need to touch the `EntityManager` yourself.

</details>

**2. Why can't `Player` stay a record now that it's an entity?**

<details><summary>Answer</summary>

JPA needs a no-argument constructor, fields it can set when loading and updating rows, and a non-final class it can subclass for lazy-loading proxies. Records have none of these: one canonical constructor, `final` fields, and a `final` class.

</details>

**3. You never implemented `findByTeamIgnoreCase`. How does it work?**

<details><summary>Answer</summary>

At startup, Spring Data parses the method name: `findBy`, then the property `Team`, then the keyword `IgnoreCase`. It builds a query from that, roughly `select p from Player p where upper(p.team) = upper(?1)`. A typo such as `findByTeem` makes startup fail, so you find out immediately rather than at runtime.

</details>

**4. What does `ddl-auto=create-drop` do? Why is it fine here but dangerous in production?**

<details><summary>Answer</summary>

Hibernate creates the tables from your entities at startup and drops them at shutdown. That's convenient for an in-memory development database, but in production it would delete all your data. Production systems use `validate` (or `none`) together with a migration tool such as Flyway, which applies versioned SQL scripts.

</details>

**5. What does `@DataJpaTest` give you compared with `@SpringBootTest`?**

<details><summary>Answer</summary>

A slice with only the JPA infrastructure: entities, repositories, an embedded database, and a transaction per test that's rolled back afterwards. It's faster and isolated. `@SpringBootTest` starts the whole application, including the web layer and your `CommandLineRunner` beans.

</details>

**6. When do you use `@Mock`, and when `@MockitoBean`?**

<details><summary>Answer</summary>

- `@Mock` (with `MockitoExtension`) creates a plain Mockito mock for a unit test with no Spring context at all. It's the faster choice when you don't need Spring.
- `@MockitoBean` creates a mock *and registers it as a bean* in a Spring test context, replacing any real bean of that type. Use it in slice tests like `@WebMvcTest`, or in a full `@SpringBootTest`.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 4: persistence with Spring Data JPA and H2"
```

## Chapter 5: Validation and error handling

**Goal:** Reject bad input with a clear 400 response, and turn "not found" and "already exists" into proper 404 and 409 responses, all in one consistent JSON error format.

**You'll learn:** Bean Validation (`@Valid`, `@NotBlank`, `@Size`), custom exceptions, `@RestControllerAdvice` and `@ExceptionHandler`, `ResponseEntityExceptionHandler`, and Problem Details (RFC 9457).

### Concept primer

The API has three problems right now:

- `POST /api/players` with `{"name": ""}` creates a player with an empty name.
- Creating "Stephen Curry" a second time fails deep in the database on the unique constraint, and the client gets a 500 Internal Server Error.
- The 404 logic lives in the controller, so every new controller would have to repeat it.

Three tools fix these:

1. **Bean Validation** (the Jakarta Validation standard, implemented by Hibernate Validator). You annotate fields with constraints such as `@NotBlank` or `@Size(max = 100)`, and put `@Valid` on the `@RequestBody` parameter. Spring validates the object right after reading the JSON. If a constraint fails, Spring throws `MethodArgumentNotValidException` and your method never runs.
2. **Exceptions for business errors.** The service throws `PlayerNotFoundException` or `DuplicatePlayerException`. It knows nothing about HTTP; it only reports what went wrong.
3. **A global exception handler.** A class annotated with `@RestControllerAdvice` applies to every controller. Its `@ExceptionHandler` methods translate exceptions into HTTP responses, in one place.

For the error body you'll use **Problem Details** (RFC 9457), a standard JSON format with the content type `application/problem+json`. Spring represents it with the `ProblemDetail` class:

```json
{"detail":"Player 99 not found","instance":"/api/players/99","status":404,"title":"Not Found"}
```

```text
POST /api/players {"name": ""}
     |
     v
DispatcherServlet: read JSON, then @Valid --fails--> MethodArgumentNotValidException --+
     |                                                                                |
     | valid                                                                          v
     v                                                                  GlobalExceptionHandler
PlayerController --> PlayerService --throws--> DuplicatePlayerException -------> (@RestControllerAdvice)
                                                                                      |
                                                              400 / 404 / 409 with application/problem+json
```

### Step 5.1: Validate the request body

**Task:**

1. Add `spring-boot-starter-validation` to `pom.xml`.
2. Annotate `CreatePlayerRequest`. `name` must not be blank and can be at most 100 characters. `team` is optional and can be at most 100 characters.
3. In `PlayerController`, put `@Valid` in front of `@RequestBody`. (You'll see the full controller in Step 5.3.)

<details><summary>Hint</summary>

The constraints live in `jakarta.validation.constraints`, and `@Valid` in `jakarta.validation`. Annotations on record components apply to the underlying fields, which is where the validator looks.

</details>

<details><summary>Solution</summary>

**Excerpt:** `nba-props/pom.xml`

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/CreatePlayerRequest.java`

```java
package com.example.nbaprops.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlayerRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String team) {
}
```

</details>

### Step 5.2: Throw exceptions from the service

**Task:**

1. In a new package `com.example.nbaprops.error`, create two abstract exception classes, `NotFoundException` and `ConflictException`. Each extends `RuntimeException` and has a `protected` constructor that takes a message.
2. In the `player` package, create two concrete exceptions:
   - `PlayerNotFoundException extends NotFoundException`, with the message `Player 99 not found`.
   - `DuplicatePlayerException extends ConflictException`, with the message `A player named 'Stephen Curry' already exists`.
3. Add `boolean existsByNameIgnoreCase(String name)` to `PlayerRepository`.
4. Change `PlayerService`:
   - `findById` returns a `PlayerResponse` instead of an `Optional`, and throws `PlayerNotFoundException` when there's no such player.
   - `create` throws `DuplicatePlayerException` when a player with that name already exists, ignoring case.

<details><summary>Hint 1</summary>

Why unchecked exceptions (`RuntimeException`)? Callers don't have to declare or catch them. Also, Spring's transaction management (chapter 6) rolls back on unchecked exceptions by default.

</details>

<details><summary>Hint 2</summary>

Why the abstract base classes? The global handler can deal with every "not found" in one method, and the `error` package never needs to know about players. In chapter 6 you'll add `PropLineNotFoundException` and get its 404s for free.

</details>

<details><summary>Hint 3</summary>

`optional.orElseThrow(() -> new PlayerNotFoundException(id))` returns the value, or throws if it's empty.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/error/NotFoundException.java`

```java
package com.example.nbaprops.error;

public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/error/ConflictException.java`

```java
package com.example.nbaprops.error;

public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerNotFoundException.java`

```java
package com.example.nbaprops.player;

import com.example.nbaprops.error.NotFoundException;

public class PlayerNotFoundException extends NotFoundException {

    public PlayerNotFoundException(Long id) {
        super("Player " + id + " not found");
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/DuplicatePlayerException.java`

```java
package com.example.nbaprops.player;

import com.example.nbaprops.error.ConflictException;

public class DuplicatePlayerException extends ConflictException {

    public DuplicatePlayerException(String name) {
        super("A player named '" + name + "' already exists");
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerRepository.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeamIgnoreCase(String team);

    boolean existsByNameIgnoreCase(String name);
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerService.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<PlayerResponse> findAll(String team) {
        List<Player> players = (team == null || team.isBlank())
                ? playerRepository.findAll()
                : playerRepository.findByTeamIgnoreCase(team);
        return players.stream()
                .map(PlayerResponse::from)
                .toList();
    }

    public PlayerResponse findById(Long id) {
        return playerRepository.findById(id)
                .map(PlayerResponse::from)
                .orElseThrow(() -> new PlayerNotFoundException(id));
    }

    public PlayerResponse create(CreatePlayerRequest request) {
        if (playerRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicatePlayerException(request.name());
        }
        Player saved = playerRepository.save(new Player(request.name(), request.team()));
        return PlayerResponse.from(saved);
    }
}
```

</details>

### Step 5.3: Handle exceptions in one place

**Task:** Create `com.example.nbaprops.error.GlobalExceptionHandler`:

- Annotate it with `@RestControllerAdvice`, and extend `ResponseEntityExceptionHandler`. That's Spring's base class that already turns Spring MVC's own exceptions into Problem Details.
- Add an `@ExceptionHandler(NotFoundException.class)` method that returns a `ProblemDetail` with status 404 and the exception's message as the `detail`.
- Add an `@ExceptionHandler(ConflictException.class)` method that does the same with status 409.
- Override `handleMethodArgumentNotValid` to add an `errors` property: a map from each field name to its error message.

Then simplify the controller: `getPlayer` now returns `PlayerResponse` directly, with no `ResponseEntity` or `Optional`.

<details><summary>Hint 1</summary>

`ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage())` builds the body. When a handler returns a `ProblemDetail`, Spring sets the status code and the `application/problem+json` content type for you.

</details>

<details><summary>Hint 2</summary>

Inside the override:
- `ex.getBindingResult().getFieldErrors()` lists the failures; each has `getField()` and `getDefaultMessage()`.
- `ex.getBody()` is the `ProblemDetail` Spring already prepared.
- `problem.setProperty("errors", map)` adds a custom field to it.
- Finish with `return handleExceptionInternal(ex, problem, headers, status, request);`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/error/GlobalExceptionHandler.java`

```java
package com.example.nbaprops.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ex.getBody();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerController.java`

```java
package com.example.nbaprops.player;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public List<PlayerResponse> getPlayers(@RequestParam(required = false) String team) {
        return playerService.findAll(team);
    }

    @GetMapping("/{id}")
    public PlayerResponse getPlayer(@PathVariable Long id) {
        return playerService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        PlayerResponse created = playerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
```

</details>

### Step 5.4: Try it

Sending JSON with `curl.exe` from Windows PowerShell 5.1 is tricky, because PowerShell strips the double quotes inside arguments it passes to native programs. The reliable workaround is to pipe the JSON into `curl.exe`. `--data-binary '@-'` tells curl to read the request body from standard input.

Restart the app, then run:

```powershell
curl.exe -i http://localhost:8080/api/players/99
'{"name": "", "team": "Lakers"}' | curl.exe -i -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/players
'{"name": "Stephen Curry", "team": "Warriors"}' | curl.exe -i -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/players
```

Expected (with some headers removed):

```text
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"Player 99 not found","instance":"/api/players/99","status":404,"title":"Not Found"}

HTTP/1.1 400
Content-Type: application/problem+json

{"detail":"Invalid request content.","instance":"/api/players","status":400,"title":"Bad Request","errors":{"name":"must not be blank"}}

HTTP/1.1 409
Content-Type: application/problem+json

{"detail":"A player named 'Stephen Curry' already exists","instance":"/api/players","status":409,"title":"Conflict"}
```

From now on, use this pipe trick whenever you want to see the full response to a request that has a body. `Invoke-RestMethod` is still handy for successful requests, but in PowerShell 5.1 it throws away the body of error responses.

### Step 5.5: Test the error paths

**Task:**

- `PlayerControllerTests`:
  - Update `getPlayerReturns200WhenFound`; the service now returns the DTO directly.
  - Change the 404 test so the mocked service throws `PlayerNotFoundException`. Assert the status, the `application/problem+json` content type, and the `title`, `detail`, and `instance` fields.
  - Add a test that a blank name gives 400 with `$.errors.name` equal to `must not be blank`, and that the service is never called.
  - Add a test for the 409.
- `PlayerServiceTests`:
  - `findById` throws `PlayerNotFoundException` for an unknown id.
  - When the name already exists, `create` throws `DuplicatePlayerException` and never calls `save`.
- `PlayerRepositoryTests`:
  - `existsByNameIgnoreCase` ignores case.
  - The database itself rejects a duplicate name with a `DataIntegrityViolationException`.

<details><summary>Hint 1</summary>

Stub an exception with `when(playerService.findById(99L)).thenThrow(new PlayerNotFoundException(99L))`.

</details>

<details><summary>Hint 2</summary>

Use `content().contentType(MediaType.APPLICATION_PROBLEM_JSON)` for the content type. `verifyNoInteractions(playerService)` proves validation stopped the request before your controller method ran.

</details>

<details><summary>Hint 3</summary>

For exceptions: `assertThatThrownBy(() -> ...).isInstanceOf(...).hasMessage(...)`. To check that something wasn't called: `verify(playerRepository, never()).save(any())`.

</details>

<details><summary>Hint 4</summary>

Use `saveAndFlush`, which forces the `INSERT` to run immediately, so the constraint violation happens inside `assertThatThrownBy`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerControllerTests.java`

```java
package com.example.nbaprops.player;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void getPlayersReturnsWhatTheServiceReturns() throws Exception {
        when(playerService.findAll(null)).thenReturn(List.of(
                new PlayerResponse(1L, "Stephen Curry", "Warriors"),
                new PlayerResponse(2L, "Nikola Jokic", "Nuggets")));

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"))
                .andExpect(jsonPath("$[1].team").value("Nuggets"));
    }

    @Test
    void getPlayersPassesTheTeamFilterToTheService() throws Exception {
        when(playerService.findAll("Warriors")).thenReturn(List.of(
                new PlayerResponse(1L, "Stephen Curry", "Warriors")));

        mockMvc.perform(get("/api/players").param("team", "Warriors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"));
    }

    @Test
    void getPlayerReturns200WhenFound() throws Exception {
        when(playerService.findById(1L)).thenReturn(new PlayerResponse(1L, "Stephen Curry", "Warriors"));

        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stephen Curry"));
    }

    @Test
    void getPlayerReturnsAProblemDetailWhenMissing() throws Exception {
        when(playerService.findById(99L)).thenThrow(new PlayerNotFoundException(99L));

        mockMvc.perform(get("/api/players/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Player 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/players/99"));
    }

    @Test
    void createPlayerReturns201WithALocationHeader() throws Exception {
        when(playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers")))
                .thenReturn(new PlayerResponse(3L, "Luka Doncic", "Lakers"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Luka Doncic", "team": "Lakers"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/players/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Luka Doncic"));
    }

    @Test
    void createPlayerRejectsABlankName() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "team": "Lakers"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));

        verifyNoInteractions(playerService);
    }

    @Test
    void createPlayerReturns409ForADuplicateName() throws Exception {
        when(playerService.create(new CreatePlayerRequest("Stephen Curry", "Warriors")))
                .thenThrow(new DuplicatePlayerException("Stephen Curry"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Stephen Curry", "team": "Warriors"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A player named 'Stephen Curry' already exists"));
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerServiceTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTests {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void findAllWithoutATeamReturnsEveryPlayer() {
        when(playerRepository.findAll()).thenReturn(List.of(
                new Player("Stephen Curry", "Warriors"),
                new Player("Nikola Jokic", "Nuggets")));

        assertThat(playerService.findAll(null))
                .extracting(PlayerResponse::name)
                .containsExactly("Stephen Curry", "Nikola Jokic");
    }

    @Test
    void findAllWithATeamUsesTheTeamQuery() {
        when(playerRepository.findByTeamIgnoreCase("warriors"))
                .thenReturn(List.of(new Player("Stephen Curry", "Warriors")));

        assertThat(playerService.findAll("warriors"))
                .extracting(PlayerResponse::name)
                .containsExactly("Stephen Curry");
    }

    @Test
    void findByIdThrowsForAnUnknownId() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.findById(99L))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("Player 99 not found");
    }

    @Test
    void createSavesANewPlayer() {
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerResponse created = playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers"));

        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Luka Doncic");
        assertThat(created.team()).isEqualTo("Lakers");
    }

    @Test
    void createRejectsADuplicateName() {
        when(playerRepository.existsByNameIgnoreCase("Stephen Curry")).thenReturn(true);

        assertThatThrownBy(() -> playerService.create(new CreatePlayerRequest("Stephen Curry", "Warriors")))
                .isInstanceOf(DuplicatePlayerException.class);
        verify(playerRepository, never()).save(any());
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerRepositoryTests.java`

```java
package com.example.nbaprops.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class PlayerRepositoryTests {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void saveAssignsAnId() {
        Player saved = playerRepository.save(new Player("Stephen Curry", "Warriors"));

        assertThat(saved.getId()).isNotNull();
        assertThat(playerRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByTeamIgnoreCaseMatchesRegardlessOfCase() {
        playerRepository.save(new Player("Stephen Curry", "Warriors"));
        playerRepository.save(new Player("Nikola Jokic", "Nuggets"));

        assertThat(playerRepository.findByTeamIgnoreCase("warriors"))
                .extracting(Player::getName)
                .containsExactly("Stephen Curry");
    }

    @Test
    void existsByNameIgnoreCaseIgnoresCase() {
        playerRepository.save(new Player("Stephen Curry", "Warriors"));

        assertThat(playerRepository.existsByNameIgnoreCase("stephen curry")).isTrue();
        assertThat(playerRepository.existsByNameIgnoreCase("Luka Doncic")).isFalse();
    }

    @Test
    void theDatabaseRejectsDuplicateNames() {
        playerRepository.saveAndFlush(new Player("Stephen Curry", "Warriors"));

        assertThatThrownBy(() -> playerRepository.saveAndFlush(new Player("Stephen Curry", "Warriors")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 17, Failures: 0, Errors: 0`. `@WebMvcTest` picks up `@RestControllerAdvice` classes automatically, so the controller tests exercise your real error handler.

### Quick checks

**1. When does validation happen, and what happens when it fails?**

<details><summary>Answer</summary>

Validation runs after Jackson has turned the JSON into a `CreatePlayerRequest`, and before your controller method is called, because the parameter has `@Valid`. If a constraint fails, Spring throws `MethodArgumentNotValidException`, your method never runs, and the exception handler turns the exception into a 400.

</details>

**2. Why does the service throw exceptions instead of returning `ResponseEntity` objects or status codes?**

<details><summary>Answer</summary>

The service shouldn't know it's being called over HTTP; a scheduled job or a message listener could call it too. It reports *what* went wrong in domain terms, and the web layer (the advice) decides *how* to express that in HTTP. As a bonus, the 404 logic is no longer duplicated in every controller.

</details>

**3. What does `@RestControllerAdvice` do?**

<details><summary>Answer</summary>

It's `@ControllerAdvice` plus `@ResponseBody`: a bean whose `@ExceptionHandler` methods apply to every controller, with their return values written to the response body.

</details>

**4. The service checks `existsByNameIgnoreCase` before saving. Is that enough to prevent duplicates?**

<details><summary>Answer</summary>

No. Two requests can both pass the check before either one inserts, a race condition. The unique constraint in the database is the real guarantee; the check only produces a friendlier error in the common case. To be airtight, you'd also catch `DataIntegrityViolationException` and map it to a 409.

</details>

**5. What do 400, 404, 409, and 500 each mean?**

<details><summary>Answer</summary>

- **400 Bad Request:** the request itself is invalid, e.g. a validation error or malformed JSON.
- **404 Not Found:** the resource doesn't exist.
- **409 Conflict:** the request is valid, but it clashes with the current state, e.g. a duplicate name.
- **500 Internal Server Error:** something failed on the server that you didn't handle.

4xx means "the client should change something"; 5xx means "the server failed".

</details>

**6. Why extend `ResponseEntityExceptionHandler` instead of writing everything yourself?**

<details><summary>Answer</summary>

It already handles Spring MVC's own exceptions (malformed JSON, an unsupported media type, a missing parameter, a method that isn't allowed, and more) and returns them as Problem Details. That way every error your API returns has the same shape, and you only override the parts you want to customize, such as adding field errors.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 5: validation and error handling"
```

## Chapter 6: Prop lines, relationships and transactions

**Goal:** Build the main feature: prop lines that belong to players, with create, read, update, delete, and filters.

**You'll learn:**
- `@ManyToOne` relationships, and lazy versus eager loading
- the N+1 query problem, and fixing it with `@EntityGraph`
- `@Enumerated` and `BigDecimal`
- `@Transactional`: how Spring implements it with proxies, and dirty checking
- open-in-view
- PUT versus PATCH, and 204 No Content

### Concept primer

The domain:

```text
+-----------+ 1          many +---------------------------------------------+
| Player    |-----------------| PropLine                                    |
| id        |                 | id, player_id (foreign key), statType,      |
| name      |                 | line, overOdds, underOdds, bookmaker,       |
| team      |                 | gameTime, matchup                           |
+-----------+                 +---------------------------------------------+
```

A prop line reads like this: "Stephen Curry, POINTS, 27.5, over -110 / under -110 at draftkings, Warriors @ Nuggets." The odds are *American odds*: -110 means you bet $110 to win $100, and +105 means you bet $100 to win $105.

**Relationships.** `@ManyToOne` on `PropLine.player` means many lines point to one player, so the `prop_line` table gets a foreign key column, `player_id`. You won't add the opposite direction (a `Player.propLines` list). Keep associations one-directional unless you really need both.

**Lazy versus eager loading.** With `fetch = FetchType.LAZY`, loading a `PropLine` does *not* load its `Player`. Hibernate puts a *proxy* (a placeholder subclass) in its place, and runs a query the first time you call a method on it. `@ManyToOne` defaults to `EAGER`, which always loads the player even when you don't need it. The common best practice is to make it `LAZY` and fetch the player explicitly when you need it.

**The N+1 problem.** Lazy loading has a trap. Load N lines, then touch each line's player, and you run 1 query for the lines plus up to N queries for the players. You'll see this happen in the log, and then fix it with `@EntityGraph`, which tells Spring Data to fetch the player in the same query (with a SQL join).

**Transactions.** A transaction groups database work so that either all of it is saved or none of it is. On a method of a Spring bean, `@Transactional` starts a transaction before the method runs. It commits afterwards, or rolls back if a `RuntimeException` escapes. Spring implements this with a **proxy**: the object injected into your controller is a generated wrapper around your service, which opens and commits the transaction around each call.

```text
controller --> [proxy: begin transaction] --> PropLineService.update(...) --> [proxy: commit or roll back]
```

Inside a transaction, the entities you load are **managed**. Hibernate tracks changes to them (*dirty checking*) and writes those changes when the transaction commits, so an update doesn't need a `save()` call. `@Transactional(readOnly = true)` is a hint that nothing will be written, so Hibernate can skip dirty checking. The usual pattern is `readOnly = true` on the class, overridden by a plain `@Transactional` on each method that writes.

**Open-in-view.** By default, Spring Boot keeps the database session open for the entire web request (*open session in view*). That makes lazy loading work even inside controllers, but it holds a database connection longer and hides N+1 problems. Boot warns about it at startup; you saw the warning in chapter 4. You'll turn it off. The consequence: lazy associations must be loaded inside the service's transaction, which is exactly why the service converts entities to DTOs before returning.

### Step 6.1: The `StatType` enum and the `PropLine` entity

**Task:** In a new package `com.example.nbaprops.propline`:

- Create an enum `StatType` with the constants `POINTS`, `REBOUNDS`, `ASSISTS`, and `THREES`.
- Create the entity `PropLine` with the fields from the diagram:
  - `Player player`, annotated with `@ManyToOne(fetch = FetchType.LAZY, optional = false)` and `@JoinColumn(name = "player_id")`
  - `StatType statType`, annotated with `@Enumerated(EnumType.STRING)`
  - `BigDecimal line`, annotated with `@Column(nullable = false, precision = 5, scale = 1)`
  - `int overOdds` and `int underOdds`
  - `String bookmaker`, `Instant gameTime`, and `String matchup`. Only `matchup` may be null.

  Give it a `protected` no-argument constructor, a constructor that sets every field except `id`, an `update(...)` method that takes the same parameters, and getters.

<details><summary>Hint 1</summary>

`EnumType.STRING` stores the name, like `"POINTS"`. The default, `ORDINAL`, stores the position (0, 1, 2, ...), which silently breaks if you ever reorder the constants.

</details>

<details><summary>Hint 2</summary>

Why `BigDecimal` for the line? A `double` can't represent many decimal numbers exactly (in Java, `0.1 + 0.2 != 0.3`), and lines are exact values like 27.5 that you'll compare. `precision = 5, scale = 1` allows values up to 9999.9, with one digit after the decimal point.

</details>

<details><summary>Hint 3</summary>

`Instant` is a single point on the timeline, in UTC. It's the right type for "when does the game start", whatever the viewer's time zone.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/StatType.java`

```java
package com.example.nbaprops.propline;

public enum StatType {
    POINTS,
    REBOUNDS,
    ASSISTS,
    THREES
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLine.java`

```java
package com.example.nbaprops.propline;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import com.example.nbaprops.player.Player;

@Entity
public class PropLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatType statType;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal line;

    @Column(nullable = false)
    private int overOdds;

    @Column(nullable = false)
    private int underOdds;

    @Column(nullable = false)
    private String bookmaker;

    @Column(nullable = false)
    private Instant gameTime;

    private String matchup;

    protected PropLine() {
        // required by JPA
    }

    public PropLine(Player player, StatType statType, BigDecimal line, int overOdds, int underOdds,
            String bookmaker, Instant gameTime, String matchup) {
        this.player = player;
        this.statType = statType;
        this.line = line;
        this.overOdds = overOdds;
        this.underOdds = underOdds;
        this.bookmaker = bookmaker;
        this.gameTime = gameTime;
        this.matchup = matchup;
    }

    public void update(Player player, StatType statType, BigDecimal line, int overOdds, int underOdds,
            String bookmaker, Instant gameTime, String matchup) {
        this.player = player;
        this.statType = statType;
        this.line = line;
        this.overOdds = overOdds;
        this.underOdds = underOdds;
        this.bookmaker = bookmaker;
        this.gameTime = gameTime;
        this.matchup = matchup;
    }

    public Long getId() {
        return id;
    }

    public Player getPlayer() {
        return player;
    }

    public StatType getStatType() {
        return statType;
    }

    public BigDecimal getLine() {
        return line;
    }

    public int getOverOdds() {
        return overOdds;
    }

    public int getUnderOdds() {
        return underOdds;
    }

    public String getBookmaker() {
        return bookmaker;
    }

    public Instant getGameTime() {
        return gameTime;
    }

    public String getMatchup() {
        return matchup;
    }
}
```

</details>

### Step 6.2: The repository, the DTOs, and a not-found exception

**Task:**

- Create `PropLineRepository`, extending `JpaRepository<PropLine, Long>`, with three derived queries: `findByPlayerId`, `findByStatType`, and `findByPlayerIdAndStatType`. Leave out `@EntityGraph` for now; Step 6.5 adds it.
- Create a `PropLineRequest` record, used for both create and update. Its components are `playerId`, `statType`, `line`, `overOdds`, `underOdds`, `bookmaker`, `gameTime`, and `matchup`, with these rules:
  - Everything except `matchup` is required.
  - `line` is at least 0.5, with at most one decimal place.
  - `bookmaker` is not blank and at most 50 characters.
  - `matchup` is at most 100 characters.
- Create a `PropLineResponse` record with `id`, `playerId`, `playerName`, `statType`, `line`, `overOdds`, `underOdds`, `bookmaker`, `gameTime`, and `matchup`, plus a static `from(PropLine)` method.
- Create `PropLineNotFoundException`, extending `NotFoundException`, with the message `Prop line 7 not found`.

<details><summary>Hint 1</summary>

`findByPlayerId` works even though `PropLine` has no `playerId` field. Spring Data splits the name into the property path `player.id`.

</details>

<details><summary>Hint 2</summary>

Declare the odds as `Integer`, not `int`, in the request. If the JSON leaves out a primitive `int`, Jackson sets it to `0`, and `@NotNull` can't catch that. With `Integer`, a missing value is `null`.

</details>

<details><summary>Hint 3</summary>

Use `@DecimalMin("0.5")` and `@Digits(integer = 3, fraction = 1)` for the line.

</details>

<details><summary>Solution</summary>

**Temporary:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineRepository.java` (you'll improve it in Step 6.5)

```java
package com.example.nbaprops.propline;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropLineRepository extends JpaRepository<PropLine, Long> {

    List<PropLine> findByPlayerId(Long playerId);

    List<PropLine> findByStatType(StatType statType);

    List<PropLine> findByPlayerIdAndStatType(Long playerId, StatType statType);
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineRequest.java`

```java
package com.example.nbaprops.propline;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PropLineRequest(
        @NotNull Long playerId,
        @NotNull StatType statType,
        @NotNull @DecimalMin("0.5") @Digits(integer = 3, fraction = 1) BigDecimal line,
        @NotNull Integer overOdds,
        @NotNull Integer underOdds,
        @NotBlank @Size(max = 50) String bookmaker,
        @NotNull Instant gameTime,
        @Size(max = 100) String matchup) {
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineResponse.java`

```java
package com.example.nbaprops.propline;

import java.math.BigDecimal;
import java.time.Instant;

public record PropLineResponse(
        Long id,
        Long playerId,
        String playerName,
        StatType statType,
        BigDecimal line,
        int overOdds,
        int underOdds,
        String bookmaker,
        Instant gameTime,
        String matchup) {

    public static PropLineResponse from(PropLine propLine) {
        return new PropLineResponse(
                propLine.getId(),
                propLine.getPlayer().getId(),
                propLine.getPlayer().getName(),
                propLine.getStatType(),
                propLine.getLine(),
                propLine.getOverOdds(),
                propLine.getUnderOdds(),
                propLine.getBookmaker(),
                propLine.getGameTime(),
                propLine.getMatchup());
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineNotFoundException.java`

```java
package com.example.nbaprops.propline;

import com.example.nbaprops.error.NotFoundException;

public class PropLineNotFoundException extends NotFoundException {

    public PropLineNotFoundException(Long id) {
        super("Prop line " + id + " not found");
    }
}
```

</details>

### Step 6.3: The service, with transactions

**Task:** Create `PropLineService`, annotated with `@Service` and `@Transactional(readOnly = true)`:

- `search(Long playerId, StatType statType)` calls the right repository method for whichever filters are present.
- `findById(id)` throws `PropLineNotFoundException` if there's no such line.
- `create(request)`, annotated with `@Transactional`, loads the player (throwing `PlayerNotFoundException` if it's missing) and saves a new line.
- `update(id, request)`, annotated with `@Transactional`, loads the line and the player, then calls `propLine.update(...)`. It does **not** call `save`.
- `delete(id)`, annotated with `@Transactional`, throws `PropLineNotFoundException` if there's no such line.

Also update `PlayerService`: add `@Transactional(readOnly = true)` to the class, and `@Transactional` to `create`.

<details><summary>Hint 1</summary>

Always convert to `PropLineResponse` inside the service method. `PropLineResponse.from` calls `getPlayer().getName()` on a lazy proxy, which needs the open database session.

</details>

<details><summary>Hint 2</summary>

Two small private helpers, `getPropLine(id)` and `getPlayer(id)`, keep the `orElseThrow` logic in one place.

</details>

<details><summary>Hint 3</summary>

Why no `save()` in `update`? The line was loaded in this transaction, so it's managed. At commit, Hibernate notices which fields changed and issues an `UPDATE`. That's dirty checking.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineService.java`

```java
package com.example.nbaprops.propline;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nbaprops.player.Player;
import com.example.nbaprops.player.PlayerNotFoundException;
import com.example.nbaprops.player.PlayerRepository;

@Service
@Transactional(readOnly = true)
public class PropLineService {

    private final PropLineRepository propLineRepository;
    private final PlayerRepository playerRepository;

    public PropLineService(PropLineRepository propLineRepository, PlayerRepository playerRepository) {
        this.propLineRepository = propLineRepository;
        this.playerRepository = playerRepository;
    }

    public List<PropLineResponse> search(Long playerId, StatType statType) {
        List<PropLine> lines;
        if (playerId != null && statType != null) {
            lines = propLineRepository.findByPlayerIdAndStatType(playerId, statType);
        } else if (playerId != null) {
            lines = propLineRepository.findByPlayerId(playerId);
        } else if (statType != null) {
            lines = propLineRepository.findByStatType(statType);
        } else {
            lines = propLineRepository.findAll();
        }
        return lines.stream()
                .map(PropLineResponse::from)
                .toList();
    }

    public PropLineResponse findById(Long id) {
        return PropLineResponse.from(getPropLine(id));
    }

    @Transactional
    public PropLineResponse create(PropLineRequest request) {
        Player player = getPlayer(request.playerId());
        PropLine propLine = new PropLine(player, request.statType(), request.line(), request.overOdds(),
                request.underOdds(), request.bookmaker(), request.gameTime(), request.matchup());
        return PropLineResponse.from(propLineRepository.save(propLine));
    }

    @Transactional
    public PropLineResponse update(Long id, PropLineRequest request) {
        PropLine propLine = getPropLine(id);
        Player player = getPlayer(request.playerId());
        propLine.update(player, request.statType(), request.line(), request.overOdds(),
                request.underOdds(), request.bookmaker(), request.gameTime(), request.matchup());
        return PropLineResponse.from(propLine);
    }

    @Transactional
    public void delete(Long id) {
        if (!propLineRepository.existsById(id)) {
            throw new PropLineNotFoundException(id);
        }
        propLineRepository.deleteById(id);
    }

    private PropLine getPropLine(Long id) {
        return propLineRepository.findById(id)
                .orElseThrow(() -> new PropLineNotFoundException(id));
    }

    private Player getPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerService.java`

```java
package com.example.nbaprops.player;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<PlayerResponse> findAll(String team) {
        List<Player> players = (team == null || team.isBlank())
                ? playerRepository.findAll()
                : playerRepository.findByTeamIgnoreCase(team);
        return players.stream()
                .map(PlayerResponse::from)
                .toList();
    }

    public PlayerResponse findById(Long id) {
        return playerRepository.findById(id)
                .map(PlayerResponse::from)
                .orElseThrow(() -> new PlayerNotFoundException(id));
    }

    @Transactional
    public PlayerResponse create(CreatePlayerRequest request) {
        if (playerRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicatePlayerException(request.name());
        }
        Player saved = playerRepository.save(new Player(request.name(), request.team()));
        return PlayerResponse.from(saved);
    }
}
```

</details>

### Step 6.4: The controller

**Task:** Create `PropLineController`, mapped to `/api/prop-lines`:

- `GET` takes the optional request parameters `playerId` and `statType`.
- `GET /{id}` returns one line.
- `POST` returns 201 with a `Location` header.
- `PUT /{id}` returns 200 with the updated line.
- `DELETE /{id}` returns 204 No Content.

<details><summary>Hint 1</summary>

Use `@PutMapping("/{id}")` and `@DeleteMapping("/{id}")`. For the delete, return `void` and add `@ResponseStatus(HttpStatus.NO_CONTENT)`.

</details>

<details><summary>Hint 2</summary>

Spring converts `?statType=POINTS` into the enum constant for you.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineController.java`

```java
package com.example.nbaprops.propline;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/prop-lines")
public class PropLineController {

    private final PropLineService propLineService;

    public PropLineController(PropLineService propLineService) {
        this.propLineService = propLineService;
    }

    @GetMapping
    public List<PropLineResponse> search(@RequestParam(required = false) Long playerId,
            @RequestParam(required = false) StatType statType) {
        return propLineService.search(playerId, statType);
    }

    @GetMapping("/{id}")
    public PropLineResponse getPropLine(@PathVariable Long id) {
        return propLineService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PropLineResponse> createPropLine(@Valid @RequestBody PropLineRequest request) {
        PropLineResponse created = propLineService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public PropLineResponse updatePropLine(@PathVariable Long id, @Valid @RequestBody PropLineRequest request) {
        return propLineService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePropLine(@PathVariable Long id) {
        propLineService.delete(id);
    }
}
```

</details>

### Step 6.5: Seed some lines, turn off open-in-view, and fix the N+1 problem

**Task:**

1. Extend `DataSeeder` so it also saves six prop lines. It needs `PropLineRepository` as a second constructor parameter.
2. In `application.properties`, add `spring.jpa.open-in-view=false`.
3. Start the app, and run `curl.exe http://localhost:8080/api/prop-lines`. Then look at the SQL in the log:

   ```text
   Hibernate: select pl1_0.id,pl1_0.bookmaker,pl1_0.game_time,pl1_0.line,pl1_0.matchup,pl1_0.over_odds,pl1_0.player_id,pl1_0.stat_type,pl1_0.under_odds from prop_line pl1_0
   Hibernate: select p1_0.id,p1_0.name,p1_0.team from player p1_0 where p1_0.id=?
   Hibernate: select p1_0.id,p1_0.name,p1_0.team from player p1_0 where p1_0.id=?
   Hibernate: select p1_0.id,p1_0.name,p1_0.team from player p1_0 where p1_0.id=?
   Hibernate: select p1_0.id,p1_0.name,p1_0.team from player p1_0 where p1_0.id=?
   ```

   That's the N+1 problem: one query for the six lines, then one query per player. There are four player queries, not six, because Hibernate reuses players it has already loaded in this transaction. With 500 lines from 200 players, a single request would run 201 queries.

4. Fix it. Add `@EntityGraph(attributePaths = "player")` to the three finder methods, and override `findAll()` with the same annotation. Restart and call the endpoint again:

   ```text
   Hibernate: select pl1_0.id,pl1_0.bookmaker,pl1_0.game_time,pl1_0.line,pl1_0.matchup,pl1_0.over_odds,pl1_0.player_id,p1_0.id,p1_0.name,p1_0.team,pl1_0.stat_type,pl1_0.under_odds from prop_line pl1_0 join player p1_0 on p1_0.id=pl1_0.player_id
   ```

   Now it's one query.

<details><summary>Hint</summary>

To add an annotation to an inherited method, redeclare it in your interface: `@Override @EntityGraph(attributePaths = "player") List<PropLine> findAll();`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/config/DataSeeder.java`

```java
package com.example.nbaprops.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.nbaprops.player.Player;
import com.example.nbaprops.player.PlayerRepository;
import com.example.nbaprops.propline.PropLine;
import com.example.nbaprops.propline.PropLineRepository;
import com.example.nbaprops.propline.StatType;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PlayerRepository playerRepository;
    private final PropLineRepository propLineRepository;

    public DataSeeder(PlayerRepository playerRepository, PropLineRepository propLineRepository) {
        this.playerRepository = playerRepository;
        this.propLineRepository = propLineRepository;
    }

    @Override
    public void run(String... args) {
        if (playerRepository.count() > 0) {
            return;
        }
        Player curry = new Player("Stephen Curry", "Warriors");
        Player jokic = new Player("Nikola Jokic", "Nuggets");
        Player shai = new Player("Shai Gilgeous-Alexander", "Thunder");
        Player wemby = new Player("Victor Wembanyama", "Spurs");
        playerRepository.saveAll(List.of(curry, jokic, shai, wemby));

        Instant nuggetsGame = Instant.parse("2026-10-22T02:00:00Z");
        Instant thunderGame = Instant.parse("2026-10-22T00:30:00Z");
        propLineRepository.saveAll(List.of(
                line(curry, StatType.POINTS, "27.5", -110, -110, nuggetsGame, "Warriors @ Nuggets"),
                line(curry, StatType.THREES, "4.5", 105, -135, nuggetsGame, "Warriors @ Nuggets"),
                line(jokic, StatType.POINTS, "26.5", -115, -105, nuggetsGame, "Warriors @ Nuggets"),
                line(jokic, StatType.REBOUNDS, "12.5", -110, -110, nuggetsGame, "Warriors @ Nuggets"),
                line(shai, StatType.POINTS, "31.5", -110, -110, thunderGame, "Spurs @ Thunder"),
                line(wemby, StatType.REBOUNDS, "11.5", -105, -115, thunderGame, "Spurs @ Thunder")));
        log.info("Seeded {} players and {} prop lines", playerRepository.count(), propLineRepository.count());
    }

    private static PropLine line(Player player, StatType statType, String line, int overOdds, int underOdds,
            Instant gameTime, String matchup) {
        return new PropLine(player, statType, new BigDecimal(line), overOdds, underOdds, "draftkings",
                gameTime, matchup);
    }
}
```

**File:** `nba-props/src/main/resources/application.properties`

```properties
spring.application.name=nba-props

# In-memory H2 database. The fixed name lets you connect to it from the H2 console.
spring.datasource.url=jdbc:h2:mem:nbaprops
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
spring.h2.console.enabled=true
```

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineRepository.java`

```java
package com.example.nbaprops.propline;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropLineRepository extends JpaRepository<PropLine, Long> {

    @Override
    @EntityGraph(attributePaths = "player")
    List<PropLine> findAll();

    @EntityGraph(attributePaths = "player")
    List<PropLine> findByPlayerId(Long playerId);

    @EntityGraph(attributePaths = "player")
    List<PropLine> findByStatType(StatType statType);

    @EntityGraph(attributePaths = "player")
    List<PropLine> findByPlayerIdAndStatType(Long playerId, StatType statType);
}
```

</details>

The open-in-view warning is also gone from the startup log.

### Step 6.6: Try the whole API

`Format-Table` picks a few columns, so the output fits the screen:

```powershell
Invoke-RestMethod http://localhost:8080/api/prop-lines | Format-Table id, playerName, statType, line, overOdds, underOdds
```

```text
id playerName              statType line overOdds underOdds
-- ----------              -------- ---- -------- ---------
 1 Stephen Curry           POINTS   27.5     -110      -110
 2 Stephen Curry           THREES    4.5      105      -135
 3 Nikola Jokic            POINTS   26.5     -115      -105
 4 Nikola Jokic            REBOUNDS 12.5     -110      -110
 5 Shai Gilgeous-Alexander POINTS   31.5     -110      -110
 6 Victor Wembanyama       REBOUNDS 11.5     -105      -115
```

Filter, then create, update, and delete a line:

```powershell
curl.exe "http://localhost:8080/api/prop-lines?playerId=2&statType=REBOUNDS"
'{"playerId": 3, "statType": "ASSISTS", "line": 6.5, "overOdds": -120, "underOdds": 100, "bookmaker": "fanduel", "gameTime": "2026-10-22T00:30:00Z", "matchup": "Spurs @ Thunder"}' | curl.exe -i -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/prop-lines
'{"playerId": 3, "statType": "ASSISTS", "line": 7.5, "overOdds": 110, "underOdds": -130, "bookmaker": "fanduel", "gameTime": "2026-10-22T00:30:00Z", "matchup": "Spurs @ Thunder"}' | curl.exe -i -X PUT -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/prop-lines/7
curl.exe -i -X DELETE http://localhost:8080/api/prop-lines/7
curl.exe -i -X DELETE http://localhost:8080/api/prop-lines/7
curl.exe -i "http://localhost:8080/api/prop-lines?statType=points"
```

What you should see:

1. Only Jokic's rebounds line.
2. `201` with `Location: http://localhost:8080/api/prop-lines/7`.
3. `200` with `"line":7.5`.
4. `204` with no body.
5. `404` with `"detail":"Prop line 7 not found"`.
6. `400` with `"detail":"Failed to convert 'statType' with value: 'points'"`. Enum names are case-sensitive.

In the JSON, `gameTime` appears as `"2026-10-22T02:00:00Z"`: Jackson writes an `Instant` as an ISO-8601 string.

### Step 6.7: Test it

**Task:**

- `PropLineRepositoryTests` (`@DataJpaTest`):
  - In a `@BeforeEach`, persist two players and three lines with `TestEntityManager`.
  - Test each finder method.
  - Test that the lines `findAll()` returns already have their player loaded, which proves the entity graph works.
- `PropLineServiceTests` (Mockito):
  - `search` with both filters uses the combined query.
  - `search` with no filters uses `findAll`.
  - `create` with an unknown player throws and never saves.
  - `update` changes the entity without calling `save`.
  - `delete` of a missing line throws and never deletes.
- `PropLineControllerTests` (`@WebMvcTest`):
  - `search` passes both filters through.
  - An unknown `statType` gives 400.
  - An unknown id gives 404.
  - Create gives 201 with a `Location` header.
  - A missing `line` gives 400 with `$.errors.line`.
  - Update gives 200.
  - Delete gives 204, and the service was called.

<details><summary>Hint 1</summary>

`TestEntityManager` (from `org.springframework.boot.jpa.test.autoconfigure`) persists test data directly. After the setup, call `flush()` and then `clear()`, so the repository has to load everything from the database instead of reusing objects already in memory.

</details>

<details><summary>Hint 2</summary>

`Hibernate.isInitialized(line.getPlayer())` returns `false` for a lazy proxy that hasn't been loaded. Try removing `@EntityGraph` from `findAll()`: that test fails.

</details>

<details><summary>Hint 3</summary>

Compare `BigDecimal` values with `isEqualByComparingTo("28.5")`. `equals` also compares the scale, so `28.5` and `28.50` aren't `equals`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/propline/PropLineRepositoryTests.java`

```java
package com.example.nbaprops.propline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.nbaprops.player.Player;

@DataJpaTest
class PropLineRepositoryTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PropLineRepository propLineRepository;

    private Player curry;
    private Player jokic;

    @BeforeEach
    void setUp() {
        curry = entityManager.persist(new Player("Stephen Curry", "Warriors"));
        jokic = entityManager.persist(new Player("Nikola Jokic", "Nuggets"));
        entityManager.persist(line(curry, StatType.POINTS));
        entityManager.persist(line(curry, StatType.THREES));
        entityManager.persist(line(jokic, StatType.POINTS));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findByPlayerIdReturnsOnlyThatPlayersLines() {
        assertThat(propLineRepository.findByPlayerId(curry.getId()))
                .extracting(PropLine::getStatType)
                .containsExactlyInAnyOrder(StatType.POINTS, StatType.THREES);
    }

    @Test
    void findByStatTypeReturnsLinesForEveryPlayer() {
        assertThat(propLineRepository.findByStatType(StatType.POINTS))
                .extracting(line -> line.getPlayer().getName())
                .containsExactlyInAnyOrder("Stephen Curry", "Nikola Jokic");
    }

    @Test
    void findByPlayerIdAndStatTypeCombinesBothFilters() {
        assertThat(propLineRepository.findByPlayerIdAndStatType(jokic.getId(), StatType.POINTS)).hasSize(1);
        assertThat(propLineRepository.findByPlayerIdAndStatType(jokic.getId(), StatType.THREES)).isEmpty();
    }

    @Test
    void findAllLoadsEachPlayerInTheSameQuery() {
        List<PropLine> lines = propLineRepository.findAll();

        assertThat(lines).hasSize(3)
                .allSatisfy(line -> assertThat(Hibernate.isInitialized(line.getPlayer())).isTrue());
    }

    private static PropLine line(Player player, StatType statType) {
        return new PropLine(player, statType, new BigDecimal("20.5"), -110, -110, "draftkings",
                Instant.parse("2026-10-22T02:00:00Z"), "Warriors @ Nuggets");
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/propline/PropLineServiceTests.java`

```java
package com.example.nbaprops.propline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nbaprops.player.Player;
import com.example.nbaprops.player.PlayerNotFoundException;
import com.example.nbaprops.player.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class PropLineServiceTests {

    private static final Instant GAME_TIME = Instant.parse("2026-10-22T02:00:00Z");

    @Mock
    private PropLineRepository propLineRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PropLineService propLineService;

    private final Player curry = new Player("Stephen Curry", "Warriors");

    @Test
    void searchWithBothFiltersUsesTheCombinedQuery() {
        when(propLineRepository.findByPlayerIdAndStatType(1L, StatType.POINTS))
                .thenReturn(List.of(line("27.5")));

        assertThat(propLineService.search(1L, StatType.POINTS))
                .extracting(PropLineResponse::playerName)
                .containsExactly("Stephen Curry");
    }

    @Test
    void searchWithoutFiltersReturnsEveryLine() {
        when(propLineRepository.findAll()).thenReturn(List.of(line("27.5"), line("4.5")));

        assertThat(propLineService.search(null, null)).hasSize(2);
    }

    @Test
    void createRejectsAnUnknownPlayer() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propLineService.create(request(99L, "27.5")))
                .isInstanceOf(PlayerNotFoundException.class);
        verify(propLineRepository, never()).save(any());
    }

    @Test
    void updateChangesTheManagedEntityWithoutCallingSave() {
        PropLine existing = line("27.5");
        when(propLineRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(curry));

        PropLineResponse updated = propLineService.update(5L, request(1L, "28.5"));

        assertThat(updated.line()).isEqualByComparingTo("28.5");
        assertThat(existing.getLine()).isEqualByComparingTo("28.5");
        verify(propLineRepository, never()).save(any());
    }

    @Test
    void deleteThrowsWhenTheLineDoesNotExist() {
        when(propLineRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> propLineService.delete(5L))
                .isInstanceOf(PropLineNotFoundException.class);
        verify(propLineRepository, never()).deleteById(any());
    }

    private PropLine line(String line) {
        return new PropLine(curry, StatType.POINTS, new BigDecimal(line), -110, -110, "draftkings",
                GAME_TIME, "Warriors @ Nuggets");
    }

    private static PropLineRequest request(Long playerId, String line) {
        return new PropLineRequest(playerId, StatType.POINTS, new BigDecimal(line), -115, -105, "draftkings",
                GAME_TIME, "Warriors @ Nuggets");
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/propline/PropLineControllerTests.java`

```java
package com.example.nbaprops.propline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PropLineController.class)
class PropLineControllerTests {

    private static final String VALID_REQUEST = """
            {
              "playerId": 1,
              "statType": "POINTS",
              "line": 27.5,
              "overOdds": -110,
              "underOdds": -110,
              "bookmaker": "draftkings",
              "gameTime": "2026-10-22T02:00:00Z",
              "matchup": "Warriors @ Nuggets"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropLineService propLineService;

    @Test
    void searchPassesBothFiltersToTheService() throws Exception {
        when(propLineService.search(1L, StatType.POINTS)).thenReturn(List.of(response(7L)));

        mockMvc.perform(get("/api/prop-lines").param("playerId", "1").param("statType", "POINTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].playerName").value("Stephen Curry"))
                .andExpect(jsonPath("$[0].line").value(27.5))
                .andExpect(jsonPath("$[0].gameTime").value("2026-10-22T02:00:00Z"));
    }

    @Test
    void searchRejectsAnUnknownStatType() throws Exception {
        mockMvc.perform(get("/api/prop-lines").param("statType", "DUNKS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturns404ForAnUnknownLine() throws Exception {
        when(propLineService.findById(99L)).thenThrow(new PropLineNotFoundException(99L));

        mockMvc.perform(get("/api/prop-lines/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Prop line 99 not found"));
    }

    @Test
    void createReturns201WithALocationHeader() throws Exception {
        when(propLineService.create(any(PropLineRequest.class))).thenReturn(response(7L));

        mockMvc.perform(post("/api/prop-lines").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/prop-lines/7"))
                .andExpect(jsonPath("$.statType").value("POINTS"));
    }

    @Test
    void createRejectsAMissingLine() throws Exception {
        mockMvc.perform(post("/api/prop-lines").contentType(MediaType.APPLICATION_JSON).content("""
                        {"playerId": 1, "statType": "POINTS", "overOdds": -110, "underOdds": -110,
                         "bookmaker": "draftkings", "gameTime": "2026-10-22T02:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.line").value("must not be null"));
    }

    @Test
    void updateReturnsTheUpdatedLine() throws Exception {
        when(propLineService.update(eq(7L), any(PropLineRequest.class))).thenReturn(response(7L));

        mockMvc.perform(put("/api/prop-lines/7").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/prop-lines/7"))
                .andExpect(status().isNoContent());

        verify(propLineService).delete(7L);
    }

    private static PropLineResponse response(Long id) {
        return new PropLineResponse(id, 1L, "Stephen Curry", StatType.POINTS, new BigDecimal("27.5"), -110, -110,
                "draftkings", Instant.parse("2026-10-22T02:00:00Z"), "Warriors @ Nuggets");
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 33, Failures: 0, Errors: 0`.

### Quick checks

**1. What does `@ManyToOne(fetch = FetchType.LAZY)` do, and what's the default?**

<details><summary>Answer</summary>

Many prop lines point to one player, and the foreign key lives in `prop_line.player_id`. `LAZY` means the player isn't loaded until you use it; Hibernate puts a proxy in its place. The default for `@ManyToOne` is `EAGER`, which loads the player every time, whether you need it or not.

</details>

**2. What is the N+1 problem, and how do you fix it?**

<details><summary>Answer</summary>

One query loads N rows. Then touching a lazy association on each row fires one more query per row, for N+1 queries in total. The fix is to fetch the association in the first query, using `@EntityGraph(attributePaths = "player")` on the repository method or a JPQL `join fetch`. To spot it, turn on SQL logging and count the queries per request.

</details>

**3. How does `@Transactional` work under the hood, and when does it silently do nothing?**

<details><summary>Answer</summary>

Spring wraps the bean in a proxy. The proxy begins a transaction before the method runs, then commits, or rolls back if a `RuntimeException` escapes. Because it works through the proxy, it does nothing:

- when a method calls another method on `this` (*self-invocation* bypasses the proxy);
- on `private` methods;
- on objects you create with `new`, which aren't Spring beans.

</details>

**4. Why doesn't `update` call `save()`?**

<details><summary>Answer</summary>

The entity was loaded inside the transaction, so the persistence context manages it. At commit, Hibernate compares the entity's fields with a snapshot taken when it was loaded (dirty checking), and issues an `UPDATE` for whatever changed.

</details>

**5. Why use `@Enumerated(EnumType.STRING)` instead of the default?**

<details><summary>Answer</summary>

The default, `ORDINAL`, stores the constant's position (0, 1, 2). Reordering the constants, or inserting a new one in the middle, silently changes the meaning of existing rows. `STRING` stores the name (`"POINTS"`): it's readable, and safe to reorder. Only renaming a constant breaks it.

</details>

**6. What's the difference between PUT and PATCH?**

<details><summary>Answer</summary>

PUT replaces the whole resource with what you send, and it's idempotent. PATCH applies a partial change: only the fields you send are updated. This API's PUT requires every field, so it's a true replacement.

</details>

**7. You turned off open-in-view. What would break if the controller called `PropLineResponse.from(line)` on an entity the service returned?**

<details><summary>Answer</summary>

The transaction, and with it the database session, ended when the service method returned. Touching the lazy `player` proxy after that throws a `LazyInitializationException`. That's why services convert entities to DTOs inside the transaction.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 6: prop lines, relationships and transactions"
```

## Chapter 7: Integration testing

**Goal:** Prove the pieces work together. You'll write one test that runs through the whole stack (controller, service, repository, H2), and one that makes real HTTP calls.

**You'll learn:** the test pyramid, slice tests versus `@SpringBootTest`, `@AutoConfigureMockMvc`, `RANDOM_PORT` with `RestTestClient`, context caching, and keeping test data isolated.

### Concept primer

```text
          /\
         /  \       few:   full-application tests (@SpringBootTest)   slow, realistic
        /----\
       /      \     some:  slice tests (@WebMvcTest, @DataJpaTest)    medium
      /--------\
     /          \   many:  unit tests (plain JUnit and Mockito)       fast, focused
    /____________\
```

You've written the bottom two layers of this *test pyramid*. Unit and slice tests replace the neighboring layers with mocks, so they can't catch problems *between* layers: a JSON field name that doesn't match, a missing `@Transactional`, or a query that only fails against the real schema. A few full-application tests cover that gap.

`@SpringBootTest` comes in two flavors:

| | `@SpringBootTest` + `@AutoConfigureMockMvc` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` |
|---|---|---|
| Server | None; a mock servlet environment | A real Tomcat on a random free port |
| Client | `MockMvc` | `RestTestClient`, or any HTTP client |
| Speed | Faster | Somewhat slower |
| What it exercises | Everything in your application | That, plus real HTTP: the network, the port, the bytes on the wire |

**Context caching.** Starting the whole app for every test class would be slow. So Spring's test framework caches application contexts: another test class with exactly the same configuration (the same annotations, properties, and `@MockitoBean`s) reuses the cached context. Every different configuration starts a new context. That's why you should avoid giving many test classes their own unique setup, and avoid `@DirtiesContext`, which throws a context away.

**Test data isolation.** Full-application tests share one database per context, and it already contains the seeded data. There are two common strategies:

- Create your own data with unique values, and only assert on what you created. You'll do this.
- Annotate the test with `@Transactional`, so each test's changes are rolled back. This works with MockMvc, because the request runs on the test's own thread. It does *not* work with `RANDOM_PORT`, where the server handles the request on a different thread in its own transaction.

### Step 7.1: A full-application test with MockMvc

**Task:** In `src/test/java/com/example/nbaprops/`, create `ApiIntegrationTests`, annotated with `@SpringBootTest` and `@AutoConfigureMockMvc`. Write two tests:

1. The seeded data is there: `GET /api/players?team=Nuggets` returns Nikola Jokic.
2. A complete flow:
   1. Create a player named "Integration Test Player", and read its id from the response.
   2. Create an ASSISTS line for that player.
   3. Check that `GET /api/prop-lines?playerId={id}` returns exactly that one line.

<details><summary>Hint 1</summary>

`.andReturn()` gives you an `MvcResult`, and `result.getResponse().getContentAsString()` is the JSON body.

</details>

<details><summary>Hint 2</summary>

`JsonPath.parse(json).read("$.id", Long.class)` extracts a value. `JsonPath` comes from `com.jayway.jsonpath`, which is already on the test classpath.

</details>

<details><summary>Hint 3</summary>

To put the id into the JSON, write the JSON as a text block containing `%d`, then call `.formatted(playerId)` on it.

</details>

> **Boot 4 note:** `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`.

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/ApiIntegrationTests.java`

```java
package com.example.nbaprops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seededPlayersAreAvailable() throws Exception {
        mockMvc.perform(get("/api/players").param("team", "Nuggets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nikola Jokic"));
    }

    @Test
    void createAPlayerAndALineThenFindTheLineByPlayer() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Integration Test Player", "team": "Testers"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long playerId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(post("/api/prop-lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId": %d, "statType": "ASSISTS", "line": 5.5, "overOdds": -110,
                                 "underOdds": -110, "bookmaker": "testbook", "gameTime": "2026-10-22T02:00:00Z"}
                                """.formatted(playerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/prop-lines").param("playerId", String.valueOf(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].playerName").value("Integration Test Player"))
                .andExpect(jsonPath("$[0].line").value(5.5));
    }
}
```

</details>

### Step 7.2: A test over real HTTP

**Task:** Create `RealServerTests`, annotated with `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` and `@AutoConfigureRestTestClient`. Inject a `RestTestClient`, and check that `GET /api/prop-lines?statType=POINTS` returns 200, with JSON whose first element has the `statType` `POINTS`.

<details><summary>Hint 1</summary>

`restTestClient.get().uri(...).exchange()` sends the request. Then chain `expectStatus().isOk()`, `expectHeader().contentType(MediaType.APPLICATION_JSON)`, and `expectBody().jsonPath("$[0].statType").isEqualTo("POINTS")`.

</details>

<details><summary>Hint 2</summary>

`RestTestClient` is new in Spring Framework 7. It's already on your test classpath, through `spring-boot-starter-webmvc-test`.

</details>

> **Boot 4 note:** Tests written for Boot 3 usually use `TestRestTemplate`. It still exists in Boot 4 (as `org.springframework.boot.resttestclient.TestRestTemplate`), but now requires `@AutoConfigureTestRestTemplate`. `RestTestClient` is the newer option.

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/RealServerTests.java`

```java
package com.example.nbaprops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class RealServerTests {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void servesPropLinesOverRealHttp() {
        restTestClient.get().uri("/api/prop-lines?statType=POINTS")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$[0].statType").isEqualTo("POINTS");
    }
}
```

</details>

Run the whole suite:

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 36, Failures: 0, Errors: 0`. Look for the line `Tomcat started on port 55540 (http)` in the output (your port number will differ). That's the real server `RealServerTests` started.

### Quick checks

**1. Why not write *only* `@SpringBootTest` tests?**

<details><summary>Answer</summary>

They're slow, because the whole application starts, and when one fails it's harder to tell which layer broke. Unit and slice tests are fast and point straight at the problem. A few full-application tests then confirm that the layers fit together.

</details>

**2. What's the difference between `@SpringBootTest` with `@AutoConfigureMockMvc`, and `@SpringBootTest` with `webEnvironment = RANDOM_PORT`?**

<details><summary>Answer</summary>

The first starts the full context with a mock servlet environment and no network; you drive it with MockMvc. The second starts a real embedded Tomcat on a random free port, and you call it over real HTTP, here with `RestTestClient`. `RANDOM_PORT` is closer to production, and MockMvc is faster.

</details>

**3. What is context caching, and what defeats it?**

<details><summary>Answer</summary>

Spring reuses an application context across test classes whose configuration is identical. A different configuration creates another context: other annotations, properties, or profiles, or a different set of `@MockitoBean`s. `@DirtiesContext` discards a context. Each new context costs seconds, so keep test configurations uniform.

</details>

**4. `ApiIntegrationTests` creates a player named "Integration Test Player". What happens if that test runs twice in the same context, and how could you avoid it?**

<details><summary>Answer</summary>

The second run gets 409 Conflict, because the name is taken. You could generate a unique name for each run (with a UUID, for example). Or you could annotate the test class with `@Transactional`, so each test's changes are rolled back. That works with MockMvc, because the request runs on the test's thread.

</details>

**5. Why doesn't `@Transactional` on a `RANDOM_PORT` test roll back what the server wrote?**

<details><summary>Answer</summary>

The server handles the HTTP request on its own thread, in its own transaction, and that transaction commits before the response is sent. The test's transaction, on the test thread, never included that work, so rolling it back changes nothing.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 7: integration testing"
```

## Chapter 8: Profiles and PostgreSQL

**Goal:** Keep H2 for development and tests, and add a `postgres` profile that runs the same app against a real PostgreSQL database in Docker, where data survives restarts.

**You'll learn:**
- profiles and profile-specific property files
- externalized configuration and property precedence
- placeholders with default values
- environment variables for secrets, and relaxed binding
- choosing `ddl-auto` per environment, and why real projects use migrations

### Concept primer

The same code runs in several environments (your laptop, a CI server, production), each with different settings: the database URL, credentials, log levels. Spring Boot keeps configuration *outside* the code, and **profiles** let you switch between sets of it:

- `application.properties` is always loaded.
- `application-postgres.properties` is loaded only when the `postgres` profile is active, and its values override the base file's.

You activate a profile by setting `spring.profiles.active=postgres`: as a command-line argument, as an environment variable (`SPRING_PROFILES_ACTIVE`), or with `-Dspring-boot.run.profiles=postgres` when using the Maven plugin.

A value can come from several places. Higher in this list wins:

```text
1. Command-line arguments           --server.port=9090
2. Environment variables            SERVER_PORT=9090
3. Profile-specific property files  application-postgres.properties
4. application.properties
```

(The real list is longer, but these are the ones you'll use.)

**Relaxed binding** maps environment variables onto properties. Replace dots with underscores, drop dashes, and use uppercase: `spring.datasource.password` becomes `SPRING_DATASOURCE_PASSWORD`. That means you can override any property without editing a file.

**Placeholders.** In a properties file, `${POSTGRES_PASSWORD:localdev}` means: "use the value of `POSTGRES_PASSWORD` (an environment variable or another property), or `localdev` if it isn't set." Secrets belong in environment variables or a secret store, never in committed files. The default only exists to make local development convenient.

**Schema management.** On H2 you used `create-drop`. For PostgreSQL you'll use `update`, so the data survives restarts. Real projects go further: they set `validate` and use a migration tool such as Flyway, which applies versioned SQL scripts (`V1__create_tables.sql`, `V2__add_event_id.sql`). That way every environment's schema changes in the same reviewable steps. Flyway is beyond this tutorial, but be ready to explain why `update` isn't enough for production.

### Step 8.1: Add the driver and a Compose file

**Task:**

1. Add the PostgreSQL JDBC driver to `pom.xml`: `org.postgresql:postgresql`, with `runtime` scope.
2. Create `nba-props/compose.yaml` with one service called `postgres`:
   - the image `postgres:17`
   - database and user both named `nbaprops`
   - the password taken from `${POSTGRES_PASSWORD:-localdev}`
   - port 5432 published
   - a named volume mounted at `/var/lib/postgresql/data`

<details><summary>Hint 1</summary>

The official image reads the `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` environment variables when it first creates its data directory. In Compose, `${NAME:-default}` means "the environment variable `NAME`, or `default` if it isn't set".

</details>

<details><summary>Hint 2</summary>

A named volume keeps the data when the container is removed. `docker compose down -v` deletes the volume too.

</details>

<details><summary>Solution</summary>

**Excerpt:** `nba-props/pom.xml`

```xml
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```

**File:** `nba-props/compose.yaml`

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: nbaprops
      POSTGRES_USER: nbaprops
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdev}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

</details>

### Step 8.2: Create the `postgres` profile

**Task:** Create `src/main/resources/application-postgres.properties`. It sets:

- the PostgreSQL URL (`jdbc:postgresql://localhost:5432/nbaprops`) and the username
- the password, taken from `POSTGRES_PASSWORD` with the default `localdev`
- `ddl-auto=update`
- the H2 console turned off

<details><summary>Solution</summary>

**File:** `nba-props/src/main/resources/application-postgres.properties`

```properties
# Used when the "postgres" profile is active. Start the database first: docker compose up -d
spring.datasource.url=jdbc:postgresql://localhost:5432/nbaprops
spring.datasource.username=nbaprops
spring.datasource.password=${POSTGRES_PASSWORD:localdev}
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=false
```

</details>

`application.properties` doesn't change. Without a profile you still get H2, and that's what the tests use, so `.\mvnw.cmd test` doesn't need Docker. Run it now to confirm: still 36 tests, all passing.

### Step 8.3: Run against PostgreSQL

Start Docker Desktop. Then, from `nba-props`:

```powershell
docker compose up -d
docker compose ps
```

The `postgres` service should show a status of `Up`. Start the app with the profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

Look for these lines:

```text
The following 1 profile is active: "postgres"
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@38cb1606
Hibernate: create table player (id bigint generated by default as identity, name varchar(255) not null, team varchar(255), primary key (id))
...
Seeded 4 players and 6 prop lines
```

You may also see a warning like `constraint "uk2stnb33bvtgrhglixsaf658kw" of relation "player" does not exist, skipping`. It's harmless. `ddl-auto=update` drops the constraint before re-adding it, and PostgreSQL reports that there was nothing to drop.

Now create a player:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/players -ContentType 'application/json' -Body '{"name": "Luka Doncic", "team": "Lakers"}'
```

Stop the app with `Ctrl+C`, then start it again with the same command. This time there's no `create table` and no `Seeded` line, and `Invoke-RestMethod http://localhost:8080/api/players` still lists Luka Doncic. The data lives in PostgreSQL, stored in a Docker volume, so it survives restarts.

To look inside the database directly:

```powershell
docker compose exec postgres psql -U nbaprops -d nbaprops -c "select * from player;"
```

### Step 8.4: Override configuration from outside

See the precedence rules in action. First, set an environment variable:

```powershell
$env:POSTGRES_PASSWORD = "wrong-password"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

Startup fails with `FATAL: password authentication failed for user "nbaprops"`, because the environment variable replaced the `localdev` default. Clear it again:

```powershell
Remove-Item Env:POSTGRES_PASSWORD
```

A command-line argument beats everything else. This starts the app on port 9090 without touching any file:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres" "-Dspring-boot.run.arguments=--server.port=9090"
```

> **Watch out:** The postgres image only uses `POSTGRES_PASSWORD` the *first* time it creates the database. If you change the password later, the database keeps expecting the old one. To start over, delete the volume, and with it all the data: `docker compose down -v`, then `docker compose up -d`.

When you're done, `docker compose stop` stops the database and keeps the data. `docker compose down` removes the container but keeps the volume.

### Quick checks

**1. What is a profile, and how does Spring decide which property files to load?**

<details><summary>Answer</summary>

A profile is a named set of configuration (it can also switch beans on and off). Spring always loads `application.properties`, then `application-{profile}.properties` for each active profile. Values from a profile file override the base file's.

</details>

**2. Put these in order of precedence, highest first: `application.properties`, a command-line argument, an environment variable, and `application-postgres.properties`.**

<details><summary>Answer</summary>

Command-line argument, then environment variable, then `application-postgres.properties`, then `application.properties`.

</details>

**3. Which environment variable overrides `spring.datasource.url`?**

<details><summary>Answer</summary>

`SPRING_DATASOURCE_URL`. With relaxed binding, dots become underscores, dashes are dropped, and everything is uppercase.

</details>

**4. Why is the database password written as `${POSTGRES_PASSWORD:localdev}` instead of directly in the file?**

<details><summary>Answer</summary>

Secrets shouldn't be committed to git. The placeholder reads the real value from the environment at runtime, and the `localdev` default only makes local development convenient.

</details>

**5. Why use `ddl-auto=update` here, and why isn't it good enough for production?**

<details><summary>Answer</summary>

`create-drop` would wipe the data on every restart. `update` adds missing tables and columns and keeps the data. But `update` never drops or renames columns, can't transform existing data, and its changes aren't versioned or reviewed. Production systems use `validate` together with Flyway or Liquibase migrations.

</details>

**6. The tests still run on H2. What's the risk, and how would you test against a real PostgreSQL?**

<details><summary>Answer</summary>

H2 isn't PostgreSQL. Differences in SQL dialect, data types, or constraint behavior can hide bugs that only show up in production. Testcontainers solves this by starting a real PostgreSQL container for the tests, and Spring Boot supports it with `@ServiceConnection`.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 8: profiles and PostgreSQL"
```

## Chapter 9: Spring Security

**Goal:** Anyone can read players and prop lines, but only an admin can create, change, or delete them.

**You'll learn:**
- the security filter chain
- authentication versus authorization, and 401 versus 403
- HTTP Basic
- `UserDetailsService` and password hashing with BCrypt
- roles
- CSRF, and when it's safe to disable
- stateless sessions
- `@Value`
- testing security with `@WithMockUser` and `httpBasic()`

### Concept primer

Spring Security is a chain of servlet **filters** that runs *before* your request ever reaches the `DispatcherServlet`:

```text
HTTP request
     |
     v
+---------------------------------------------------------------+
| Security filter chain                                         |
|   1. Authentication: who are you? (reads the Authorization    |
|      header, checks the password)          fails --> 401      |
|   2. Authorization: are you allowed to do this?               |
|      (checks your roles against the rules) fails --> 403      |
+---------------------------------------------------------------+
     |  allowed
     v
DispatcherServlet --> controllers (and your @RestControllerAdvice)
```

- **Authentication** establishes who you are. If it fails, or you didn't send credentials, the response is **401 Unauthorized**. (The name is misleading: it really means "unauthenticated".)
- **Authorization** decides what you're allowed to do. If you're logged in but lack permission, the response is **403 Forbidden**.
- **HTTP Basic** sends `Authorization: Basic <base64 of "username:password">` with every request. Base64 is an *encoding*, not encryption, so Basic authentication is only safe over HTTPS.
- **`UserDetailsService`** looks up a user by username. You'll keep two users in memory; a real app would load them from a database.
- **`PasswordEncoder`** hashes passwords, by default with BCrypt: a deliberately slow one-way hash with a random salt. You never store or compare plain-text passwords.
- **Roles.** `.roles("ADMIN")` grants the authority `ROLE_ADMIN`, and `hasRole("ADMIN")` checks for it.

**CSRF** (cross-site request forgery) is an attack where a malicious web page makes *your browser* send a request to a site you're logged into, and the browser automatically attaches your session cookie. Spring Security blocks this by requiring a secret token on every POST, PUT, and DELETE. This API doesn't use cookies or sessions: clients send credentials explicitly in a header on every request, and the browser won't add that header for another site. So you can disable CSRF protection, and make sessions **stateless**.

### Step 9.1: Add Spring Security and watch what breaks

**Task:** Add `spring-boot-starter-security` to `pom.xml`, and `spring-boot-starter-security-test` with `test` scope. Then run the tests and start the app, *before* writing any configuration.

<details><summary>Solution</summary>

**Excerpt:** `nba-props/pom.xml`

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```

**Excerpt:** `nba-props/pom.xml`

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

</details>

```powershell
.\mvnw.cmd test
```

Most controller tests fail:

```text
PlayerControllerTests.getPlayersReturnsWhatTheServiceReturns:37 Status expected:<200> but was:<401>
PlayerControllerTests.createPlayerReturns201WithALocationHeader:85 Status expected:<201> but was:<403>
...
Tests run: 36, Failures: 15, Errors: 0, Skipped: 0
```

Just by being on the classpath, Spring Security locks everything down. Every request needs a login (the 401s), and CSRF protection rejects every POST, PUT, and DELETE that doesn't carry a CSRF token (the 403s). Start the app and you'll also see:

```text
Using generated security password: f86746aa-e110-4002-9bc2-1ae89403e85b

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

That's Boot's default: a single user named `user`, with a random password. Next, you'll replace the defaults with your own rules.

### Step 9.2: Write the security rules

**Task:** Create `com.example.nbaprops.config.SecurityConfig`, annotated with `@Configuration`, with a `@Bean` method that returns a `SecurityFilterChain`:

- `GET /api/**` is open to everyone.
- Any other request under `/api/**` needs the `ADMIN` role.
- `/h2-console/**` and `/error` are open to everyone.
- Everything else requires authentication.
- Authenticate with HTTP Basic.
- Disable CSRF protection and use stateless sessions.
- Allow pages to be framed by the same origin, because the H2 console uses HTML frames.

<details><summary>Hint 1</summary>

The method receives an `HttpSecurity` builder and ends with `return http.build();`. The rules go in `http.authorizeHttpRequests(auth -> auth.requestMatchers(...).permitAll() ...)`. Rules are checked in order and the first match wins, so put the specific ones first and end with `.anyRequest().authenticated()`.

</details>

<details><summary>Hint 2</summary>

The other settings are:
- `.httpBasic(Customizer.withDefaults())`
- `.csrf(csrf -> csrf.disable())`
- `.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))`
- `.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`

</details>

<details><summary>Hint 3</summary>

Why permit `/error`? When something fails, Boot forwards to `/error` to render the error response. If that path required a login, an anonymous client would get a 401 instead of the real error.

</details>

The complete `SecurityConfig`, including the users from the next step, is in the Step 9.3 solution.

### Step 9.3: Define the users

**Task:** In the same class, add two more beans:

- A `PasswordEncoder`, from `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.
- A `UserDetailsService`: an `InMemoryUserDetailsManager` holding `user` (role `USER`) and `admin` (role `ADMIN`). Their passwords are hashed with the encoder, and read from configuration with `@Value`.

In `application.properties`, add the two passwords as placeholders with local defaults: `${USER_PASSWORD:user-password}` and `${ADMIN_PASSWORD:admin-password}`.

<details><summary>Hint 1</summary>

`@Value("${nba-props.security.admin-password}") String adminPassword` can be a parameter of a `@Bean` method. Spring resolves the property and passes the value in.

</details>

<details><summary>Hint 2</summary>

Build each user with `User.withUsername("admin").password(passwordEncoder.encode(adminPassword)).roles("ADMIN").build()`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/config/SecurityConfig.java`

```java
package com.example.nbaprops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder,
            @Value("${nba-props.security.user-password}") String userPassword,
            @Value("${nba-props.security.admin-password}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("user")
                        .password(passwordEncoder.encode(userPassword))
                        .roles("USER")
                        .build(),
                User.withUsername("admin")
                        .password(passwordEncoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

**File:** `nba-props/src/main/resources/application.properties`

```properties
spring.application.name=nba-props

# In-memory H2 database. The fixed name lets you connect to it from the H2 console.
spring.datasource.url=jdbc:h2:mem:nbaprops
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
spring.h2.console.enabled=true

# Passwords for the two built-in users. The defaults are for local development only;
# set USER_PASSWORD and ADMIN_PASSWORD in any shared or deployed environment.
nba-props.security.user-password=${USER_PASSWORD:user-password}
nba-props.security.admin-password=${ADMIN_PASSWORD:admin-password}
```

</details>

The generated-password message is gone from the startup log, because you now provide your own `UserDetailsService`.

`createDelegatingPasswordEncoder()` hashes with BCrypt and stores the algorithm name in front of the hash, as `{bcrypt}$2a$10$...`. That way you can switch algorithms later without breaking existing passwords.

### Step 9.4: Try it

Restart the app and send one request as each kind of caller. `curl.exe -u name:password` adds the Basic `Authorization` header for you.

```powershell
curl.exe -i http://localhost:8080/api/players
'{"name": "Luka Doncic", "team": "Lakers"}' | curl.exe -i -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/players
'{"name": "Luka Doncic", "team": "Lakers"}' | curl.exe -i -u user:user-password -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/players
'{"name": "Luka Doncic", "team": "Lakers"}' | curl.exe -i -u admin:admin-password -H "Content-Type: application/json" --data-binary '@-' http://localhost:8080/api/players
```

Expected (trimmed):

```text
HTTP/1.1 200                       <- anyone can read

HTTP/1.1 401                       <- no credentials
WWW-Authenticate: Basic realm="Realm", charset="UTF-8"
{"timestamp":"...","status":401,"error":"Unauthorized","path":"/api/players"}

HTTP/1.1 403                       <- logged in as user, but not an admin
{"timestamp":"...","status":403,"error":"Forbidden","path":"/api/players"}

HTTP/1.1 201                       <- admin
Location: http://localhost:8080/api/players/5
```

The `WWW-Authenticate` header on the 401 tells the client *how* to authenticate. Notice that the 401 and 403 bodies aren't in your `ProblemDetail` format. Spring Security rejected those requests in its filters, before they reached the `DispatcherServlet`, so your `@RestControllerAdvice` never saw them, and Boot's default error format was used.

With `Invoke-RestMethod`, you build the header yourself. It's just Base64:

```powershell
$admin = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin-password')) }
$admin.Authorization
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/players -Headers $admin -ContentType 'application/json' -Body '{"name": "Anthony Edwards", "team": "Timberwolves"}'
```

`$admin.Authorization` prints `Basic YWRtaW46YWRtaW4tcGFzc3dvcmQ=`. Anyone who sees that header can decode the password, which is why Basic authentication must only ever travel over HTTPS.

The H2 console at http://localhost:8080/h2-console still works, thanks to the `permitAll` and `sameOrigin` rules.

### Step 9.5: Fix the tests and test the rules

**Task:**

1. Add `@Import(SecurityConfig.class)` to `PlayerControllerTests` and `PropLineControllerTests`. A `@WebMvcTest` slice doesn't load `@Configuration` classes on its own, so without the import it falls back to Boot's defaults, which is exactly what you saw in Step 9.1.
2. In those two classes, annotate every test that sends a POST, PUT, or DELETE with `@WithMockUser(roles = "ADMIN")`.
3. In `ApiIntegrationTests`, add `.with(httpBasic("admin", "admin-password"))` to the two POST requests.
4. Create `config/SecurityConfigTests`, a `@WebMvcTest(PlayerController.class)` that imports `SecurityConfig`, and test the rules themselves:
   - Anonymous GET gives 200.
   - Anonymous POST gives 401, with a `WWW-Authenticate` header that starts with `Basic`.
   - POST as `user` gives 403.
   - POST as `admin` gives 201.
   - A wrong password gives 401.

<details><summary>Hint 1</summary>

`@WithMockUser(roles = "ADMIN")` runs a test as an already-authenticated fake user. It skips the password check entirely. `httpBasic("admin", "admin-password")`, from `SecurityMockMvcRequestPostProcessors`, sends real credentials, so it exercises your actual `UserDetailsService` and `PasswordEncoder`. Use it when you're testing the login itself.

</details>

<details><summary>Hint 2</summary>

To check the start of a header, use a Hamcrest matcher: `header().string("WWW-Authenticate", startsWith("Basic"))`, with `startsWith` from `org.hamcrest.Matchers`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/player/PlayerControllerTests.java`

```java
package com.example.nbaprops.player;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nbaprops.config.SecurityConfig;

@WebMvcTest(PlayerController.class)
@Import(SecurityConfig.class)
class PlayerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void getPlayersReturnsWhatTheServiceReturns() throws Exception {
        when(playerService.findAll(null)).thenReturn(List.of(
                new PlayerResponse(1L, "Stephen Curry", "Warriors"),
                new PlayerResponse(2L, "Nikola Jokic", "Nuggets")));

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"))
                .andExpect(jsonPath("$[1].team").value("Nuggets"));
    }

    @Test
    void getPlayersPassesTheTeamFilterToTheService() throws Exception {
        when(playerService.findAll("Warriors")).thenReturn(List.of(
                new PlayerResponse(1L, "Stephen Curry", "Warriors")));

        mockMvc.perform(get("/api/players").param("team", "Warriors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Stephen Curry"));
    }

    @Test
    void getPlayerReturns200WhenFound() throws Exception {
        when(playerService.findById(1L)).thenReturn(new PlayerResponse(1L, "Stephen Curry", "Warriors"));

        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stephen Curry"));
    }

    @Test
    void getPlayerReturnsAProblemDetailWhenMissing() throws Exception {
        when(playerService.findById(99L)).thenThrow(new PlayerNotFoundException(99L));

        mockMvc.perform(get("/api/players/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Player 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/players/99"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPlayerReturns201WithALocationHeader() throws Exception {
        when(playerService.create(new CreatePlayerRequest("Luka Doncic", "Lakers")))
                .thenReturn(new PlayerResponse(3L, "Luka Doncic", "Lakers"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Luka Doncic", "team": "Lakers"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/players/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Luka Doncic"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPlayerRejectsABlankName() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "team": "Lakers"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));

        verifyNoInteractions(playerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPlayerReturns409ForADuplicateName() throws Exception {
        when(playerService.create(new CreatePlayerRequest("Stephen Curry", "Warriors")))
                .thenThrow(new DuplicatePlayerException("Stephen Curry"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Stephen Curry", "team": "Warriors"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A player named 'Stephen Curry' already exists"));
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/propline/PropLineControllerTests.java`

```java
package com.example.nbaprops.propline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nbaprops.config.SecurityConfig;

@WebMvcTest(PropLineController.class)
@Import(SecurityConfig.class)
class PropLineControllerTests {

    private static final String VALID_REQUEST = """
            {
              "playerId": 1,
              "statType": "POINTS",
              "line": 27.5,
              "overOdds": -110,
              "underOdds": -110,
              "bookmaker": "draftkings",
              "gameTime": "2026-10-22T02:00:00Z",
              "matchup": "Warriors @ Nuggets"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropLineService propLineService;

    @Test
    void searchPassesBothFiltersToTheService() throws Exception {
        when(propLineService.search(1L, StatType.POINTS)).thenReturn(List.of(response(7L)));

        mockMvc.perform(get("/api/prop-lines").param("playerId", "1").param("statType", "POINTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].playerName").value("Stephen Curry"))
                .andExpect(jsonPath("$[0].line").value(27.5))
                .andExpect(jsonPath("$[0].gameTime").value("2026-10-22T02:00:00Z"));
    }

    @Test
    void searchRejectsAnUnknownStatType() throws Exception {
        mockMvc.perform(get("/api/prop-lines").param("statType", "DUNKS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturns404ForAnUnknownLine() throws Exception {
        when(propLineService.findById(99L)).thenThrow(new PropLineNotFoundException(99L));

        mockMvc.perform(get("/api/prop-lines/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Prop line 99 not found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createReturns201WithALocationHeader() throws Exception {
        when(propLineService.create(any(PropLineRequest.class))).thenReturn(response(7L));

        mockMvc.perform(post("/api/prop-lines").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/prop-lines/7"))
                .andExpect(jsonPath("$.statType").value("POINTS"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsAMissingLine() throws Exception {
        mockMvc.perform(post("/api/prop-lines").contentType(MediaType.APPLICATION_JSON).content("""
                        {"playerId": 1, "statType": "POINTS", "overOdds": -110, "underOdds": -110,
                         "bookmaker": "draftkings", "gameTime": "2026-10-22T02:00:00Z"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.line").value("must not be null"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateReturnsTheUpdatedLine() throws Exception {
        when(propLineService.update(eq(7L), any(PropLineRequest.class))).thenReturn(response(7L));

        mockMvc.perform(put("/api/prop-lines/7").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/prop-lines/7"))
                .andExpect(status().isNoContent());

        verify(propLineService).delete(7L);
    }

    private static PropLineResponse response(Long id) {
        return new PropLineResponse(id, 1L, "Stephen Curry", StatType.POINTS, new BigDecimal("27.5"), -110, -110,
                "draftkings", Instant.parse("2026-10-22T02:00:00Z"), "Warriors @ Nuggets");
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/ApiIntegrationTests.java`

```java
package com.example.nbaprops;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seededPlayersAreAvailable() throws Exception {
        mockMvc.perform(get("/api/players").param("team", "Nuggets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nikola Jokic"));
    }

    @Test
    void createAPlayerAndALineThenFindTheLineByPlayer() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/players")
                        .with(httpBasic("admin", "admin-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Integration Test Player", "team": "Testers"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long playerId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(post("/api/prop-lines")
                        .with(httpBasic("admin", "admin-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerId": %d, "statType": "ASSISTS", "line": 5.5, "overOdds": -110,
                                 "underOdds": -110, "bookmaker": "testbook", "gameTime": "2026-10-22T02:00:00Z"}
                                """.formatted(playerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/prop-lines").param("playerId", String.valueOf(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].playerName").value("Integration Test Player"))
                .andExpect(jsonPath("$[0].line").value(5.5));
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/config/SecurityConfigTests.java`

```java
package com.example.nbaprops.config;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.nbaprops.player.CreatePlayerRequest;
import com.example.nbaprops.player.PlayerController;
import com.example.nbaprops.player.PlayerResponse;
import com.example.nbaprops.player.PlayerService;

@WebMvcTest(PlayerController.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void anyoneCanRead() throws Exception {
        when(playerService.findAll(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk());
    }

    @Test
    void writingWithoutCredentialsIs401() throws Exception {
        mockMvc.perform(createLuka())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")));
    }

    @Test
    void writingAsAPlainUserIs403() throws Exception {
        mockMvc.perform(createLuka().with(httpBasic("user", "user-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void writingAsAdminIsAllowed() throws Exception {
        when(playerService.create(any(CreatePlayerRequest.class)))
                .thenReturn(new PlayerResponse(5L, "Luka Doncic", "Lakers"));

        mockMvc.perform(createLuka().with(httpBasic("admin", "admin-password")))
                .andExpect(status().isCreated());
    }

    @Test
    void aWrongPasswordIs401() throws Exception {
        mockMvc.perform(createLuka().with(httpBasic("admin", "not-the-password")))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder createLuka() {
        return post("/api/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Luka Doncic", "team": "Lakers"}
                        """);
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 41, Failures: 0, Errors: 0`.

### Quick checks

**1. What's the difference between authentication and authorization, and between 401 and 403?**

<details><summary>Answer</summary>

Authentication establishes *who* you are; authorization decides *what* you may do. A 401 means "I don't know who you are": credentials are missing or wrong. A 403 means "I know who you are, but you're not allowed to do this".

</details>

**2. How does HTTP Basic authentication work, and why does it need HTTPS?**

<details><summary>Answer</summary>

The client sends `Authorization: Basic ` followed by the Base64 encoding of `username:password`, on every request. Base64 is trivially reversible, so without HTTPS anyone who can see the traffic can read the password.

</details>

**3. Why hash passwords with BCrypt instead of encrypting them or storing them as-is?**

<details><summary>Answer</summary>

A hash is one-way: even if the user table leaks, the passwords can't be decrypted. BCrypt adds a random salt, so identical passwords produce different hashes, and it's deliberately slow, which makes brute-force guessing expensive. To log someone in, you hash the password they typed and compare it with the stored hash.

</details>

**4. What is CSRF, and why is it safe to disable CSRF protection in this API?**

<details><summary>Answer</summary>

Cross-site request forgery tricks a logged-in user's browser into sending a request, and the browser attaches the session cookie automatically. The protection requires a token that a malicious page can't know. This API is stateless: there are no session cookies, and credentials travel in an explicit header that the browser won't attach for another site. With nothing to forge, the token adds nothing.

</details>

**5. Why don't the 401 and 403 responses use your `ProblemDetail` format?**

<details><summary>Answer</summary>

Security filters run before the `DispatcherServlet`, and `@RestControllerAdvice` only handles exceptions thrown inside Spring MVC. The rejected request never reached a controller, so Boot's default `/error` handling produced the body. To change it, you'd configure a custom `AuthenticationEntryPoint` and `AccessDeniedHandler`.

</details>

**6. Why did the `@WebMvcTest` classes need `@Import(SecurityConfig.class)`?**

<details><summary>Answer</summary>

A slice test only loads web-layer components, and `SecurityConfig` is a plain `@Configuration` class, so it wasn't included. Without it, Spring Security's defaults applied: every request required a login, and CSRF protection was on. Importing it makes the test enforce your real rules.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 9: Spring Security"
```

## Chapter 10: Importing real odds with RestClient

**Goal:** An admin calls `POST /api/admin/odds/import`, and the app pulls real NBA player-prop lines from The Odds API into the database.

**You'll learn:**
- calling an HTTP API with `RestClient`
- typed configuration with `@ConfigurationProperties`
- keeping an API key out of git
- mapping JSON to records with `@JsonProperty` and `@JsonIgnoreProperties`
- programming to an interface
- reporting upstream failures as 502 Bad Gateway
- testing HTTP clients with `MockRestServiceServer`, and testing business logic with a fake

### Before you start

- **Get a key, but only for the live run at the end.** Sign up at [the-odds-api.com](https://the-odds-api.com) for a free key. The free Starter plan gives 500 credits a month, and its pricing page says it covers all betting markets; confirm that includes player props when you sign up. No test in this chapter needs the key.
- **The NBA may be in its off-season.** The regular season usually starts in late October. Before that there may be no games with player props, and the import will simply report zero events or zero lines. You'll build and test everything against saved sample responses, so you can finish the chapter at any time of year.

### Concept primer

You'll use two calls from The Odds API (version 4):

| Call | What it returns | Cost |
|---|---|---|
| `GET /v4/sports/basketball_nba/events?apiKey=...` | Upcoming games: `id`, `commence_time`, `home_team`, `away_team` | Free: doesn't count against your quota |
| `GET /v4/sports/basketball_nba/events/{eventId}/odds?apiKey=...&regions=us&markets=player_points,...&oddsFormat=american` | One game's player-prop lines, grouped by bookmaker | Markets returned times regions: at most 4 credits per game here |

Inside the second response, player props look like this:

```json
"bookmakers": [ { "key": "draftkings", "markets": [ { "key": "player_points", "outcomes": [
    { "name": "Over",  "description": "Stephen Curry", "price": -110, "point": 27.5 },
    { "name": "Under", "description": "Stephen Curry", "price": -110, "point": 27.5 } ] } ] } ]
```

Each Over/Under pair for a player becomes one `PropLine`.

```text
OddsImportController --> OddsImportService --> OddsClient (an interface)
  POST /api/admin/          |                      ^
       odds/import          |                      | implements
                            v                TheOddsApiClient --RestClient--> api.the-odds-api.com
                  PlayerRepository,
                  PropLineRepository
```

The service depends on the `OddsClient` **interface**, not on HTTP details. In tests you pass in a fake client that returns canned data, so there's no network, no API key, and no credits spent. This is dependency injection paying off again.

- **`RestClient`** is Spring's synchronous HTTP client (Spring 6.1 and later), with a fluent API. It replaces the older `RestTemplate`. Boot auto-configures a `RestClient.Builder` bean that already has your JSON settings.
- **`@ConfigurationProperties`** binds a whole group of properties (`nba-props.odds-api.*`) to one typed record. That's easier to inject and test than a scattering of `@Value` strings.

### Step 10.1: Dependencies and configuration

**Task:**

1. Add `spring-boot-starter-restclient` to `pom.xml`.
2. Create `odds/OddsApiProperties`: a record annotated with `@ConfigurationProperties("nba-props.odds-api")`, with the components `baseUrl`, `apiKey`, `sport`, and `regions`.
3. Add `@ConfigurationPropertiesScan` to `NbaPropsApplication`.
4. In `application.properties`, set:
   - the base URL `https://api.the-odds-api.com`
   - the key from `${ODDS_API_KEY:}`, which is empty by default
   - the sport `basketball_nba`
   - the regions `us`

<details><summary>Hint</summary>

The property `base-url` binds to the record component `baseUrl`; this is relaxed binding again. `@ConfigurationPropertiesScan` finds classes annotated with `@ConfigurationProperties` and registers them as beans.

</details>

> **Boot 4 note:** In Boot 4, the auto-configuration for `RestClient.Builder` lives in its own module. Without `spring-boot-starter-restclient`, there's no builder bean to inject. In Boot 3 it came with the web starter.

<details><summary>Solution</summary>

**Excerpt:** `nba-props/pom.xml`

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restclient</artifactId>
        </dependency>
```

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsApiProperties.java`

```java
package com.example.nbaprops.odds;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nba-props.odds-api")
public record OddsApiProperties(String baseUrl, String apiKey, String sport, String regions) {
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/NbaPropsApplication.java`

```java
package com.example.nbaprops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NbaPropsApplication {

	public static void main(String[] args) {
		SpringApplication.run(NbaPropsApplication.class, args);
	}

}
```

**File:** `nba-props/src/main/resources/application.properties`

```properties
spring.application.name=nba-props

# In-memory H2 database. The fixed name lets you connect to it from the H2 console.
spring.datasource.url=jdbc:h2:mem:nbaprops
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
spring.h2.console.enabled=true

# Passwords for the two built-in users. The defaults are for local development only;
# set USER_PASSWORD and ADMIN_PASSWORD in any shared or deployed environment.
nba-props.security.user-password=${USER_PASSWORD:user-password}
nba-props.security.admin-password=${ADMIN_PASSWORD:admin-password}

# The Odds API (https://the-odds-api.com). Set ODDS_API_KEY to import live lines.
nba-props.odds-api.base-url=https://api.the-odds-api.com
nba-props.odds-api.api-key=${ODDS_API_KEY:}
nba-props.odds-api.sport=basketball_nba
nba-props.odds-api.regions=us
```

</details>

Never commit a real API key. With `${ODDS_API_KEY:}`, the key only ever lives in your environment.

### Step 10.2: Model the JSON as records

**Task:** In the `odds` package, create a record `OddsEvent` with `id`, `commenceTime`, `homeTeam`, `awayTeam`, and `bookmakers`. Nest three more records inside it:

- `Bookmaker(key, markets)`
- `Market(key, outcomes)`
- `Outcome(name, description, price, point)`

Map the snake_case JSON names with `@JsonProperty`, ignore JSON fields you don't model, and turn a missing `bookmakers` list into an empty one.

<details><summary>Hint 1</summary>

`@JsonProperty("commence_time") Instant commenceTime` maps the field, and Jackson parses the ISO timestamp into an `Instant`.

</details>

<details><summary>Hint 2</summary>

Put `@JsonIgnoreProperties(ignoreUnknown = true)` on each record. The API sends fields you don't need, like `sport_title` and `last_update`.

</details>

<details><summary>Hint 3</summary>

A record's *compact constructor* runs before the fields are assigned: `public OddsEvent { bookmakers = bookmakers == null ? List.of() : bookmakers; }`. The events-list call doesn't include bookmakers at all.

</details>

> **Boot 4 note:** Jackson 3 moved its main classes to the `tools.jackson.*` packages, but the annotations stayed in `com.fasterxml.jackson.annotation`. These imports look the same as in older tutorials.

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsEvent.java`

```java
package com.example.nbaprops.odds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OddsEvent(
        String id,
        @JsonProperty("commence_time") Instant commenceTime,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,
        List<Bookmaker> bookmakers) {

    public OddsEvent {
        bookmakers = bookmakers == null ? List.of() : bookmakers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bookmaker(String key, List<Market> markets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Market(String key, List<Outcome> outcomes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Outcome(String name, String description, int price, BigDecimal point) {
    }
}
```

</details>

### Step 10.3: The client interface and its `RestClient` implementation

**Task:**

1. Create an `OddsClient` interface with two methods: `List<OddsEvent> getUpcomingEvents()` and `OddsEvent getPlayerProps(String eventId, List<String> markets)`.
2. Create `OddsApiException extends RuntimeException`.
3. Create `TheOddsApiClient implements OddsClient`, annotated with `@Component`:
   - Its constructor takes a `RestClient.Builder` and the `OddsApiProperties`.
   - It builds a `RestClient` with the base URL, and a default status handler that turns any 4xx or 5xx response into `new OddsApiException("The Odds API responded with " + status)`.
   - Before each call, it fails fast with an `OddsApiException` if no key is configured.

<details><summary>Hint 1</summary>

A request looks like this: `restClient.get().uri(uri -> uri.path("/v4/sports/{sport}/events").queryParam("apiKey", key).build(sport)).retrieve().body(...)`.

</details>

<details><summary>Hint 2</summary>

For a generic list, pass a type token: `new ParameterizedTypeReference<List<OddsEvent>>() {}`. Java erases generic types at runtime, so `List.class` alone can't say "a list of `OddsEvent`".

</details>

<details><summary>Hint 3</summary>

On the builder: `.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { throw new OddsApiException(...); })`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsClient.java`

```java
package com.example.nbaprops.odds;

import java.util.List;

public interface OddsClient {

    List<OddsEvent> getUpcomingEvents();

    OddsEvent getPlayerProps(String eventId, List<String> markets);
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsApiException.java`

```java
package com.example.nbaprops.odds;

public class OddsApiException extends RuntimeException {

    public OddsApiException(String message) {
        super(message);
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/TheOddsApiClient.java`

```java
package com.example.nbaprops.odds;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TheOddsApiClient implements OddsClient {

    private final RestClient restClient;
    private final OddsApiProperties properties;

    public TheOddsApiClient(RestClient.Builder restClientBuilder, OddsApiProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new OddsApiException("The Odds API responded with " + response.getStatusCode());
                })
                .build();
        this.properties = properties;
    }

    @Override
    public List<OddsEvent> getUpcomingEvents() {
        return restClient.get()
                .uri(uri -> uri.path("/v4/sports/{sport}/events")
                        .queryParam("apiKey", apiKey())
                        .build(properties.sport()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<OddsEvent>>() {});
    }

    @Override
    public OddsEvent getPlayerProps(String eventId, List<String> markets) {
        return restClient.get()
                .uri(uri -> uri.path("/v4/sports/{sport}/events/{eventId}/odds")
                        .queryParam("apiKey", apiKey())
                        .queryParam("regions", properties.regions())
                        .queryParam("markets", String.join(",", markets))
                        .queryParam("oddsFormat", "american")
                        .build(properties.sport(), eventId))
                .retrieve()
                .body(OddsEvent.class);
    }

    private String apiKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new OddsApiException("No API key configured. Set the ODDS_API_KEY environment variable.");
        }
        return properties.apiKey();
    }
}
```

</details>

### Step 10.4: Test the client without the network

**Task:** Save the two sample responses below as `src/test/resources/odds/events.json` and `src/test/resources/odds/event-odds.json`. Then write `TheOddsApiClientTests`, a plain JUnit test: create a `RestClient.Builder`, bind a `MockRestServiceServer` to it, and build the client from that builder. Test that:

- the events are parsed: ids, home team, commence time, and an empty bookmakers list;
- player props are parsed, and the request URL is exactly `https://api.the-odds-api.com/v4/sports/basketball_nba/events/evt-1/odds?apiKey=test-key&regions=us&markets=player_points,player_rebounds&oddsFormat=american`;
- a 401 response becomes an `OddsApiException` whose message mentions 401;
- a blank key fails before any request is sent.

<details><summary>Hint 1</summary>

`MockRestServiceServer.bindTo(builder).build()` intercepts every request from any client built with that builder.

</details>

<details><summary>Hint 2</summary>

Set up each expected request with `server.expect(requestTo("..."))`, and its response with `.andRespond(withSuccess(new ClassPathResource("odds/events.json"), MediaType.APPLICATION_JSON))`. `server.verify()` then checks that every expected request actually happened.

</details>

<details><summary>Hint 3</summary>

`withStatus(HttpStatus.UNAUTHORIZED)` simulates an error response.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/resources/odds/events.json`

```json
[
  {
    "id": "evt-1",
    "sport_key": "basketball_nba",
    "sport_title": "NBA",
    "commence_time": "2026-10-22T02:00:00Z",
    "home_team": "Denver Nuggets",
    "away_team": "Golden State Warriors"
  },
  {
    "id": "evt-2",
    "sport_key": "basketball_nba",
    "sport_title": "NBA",
    "commence_time": "2026-10-22T00:30:00Z",
    "home_team": "Oklahoma City Thunder",
    "away_team": "San Antonio Spurs"
  }
]
```

**File:** `nba-props/src/test/resources/odds/event-odds.json`

```json
{
  "id": "evt-1",
  "sport_key": "basketball_nba",
  "sport_title": "NBA",
  "commence_time": "2026-10-22T02:00:00Z",
  "home_team": "Denver Nuggets",
  "away_team": "Golden State Warriors",
  "bookmakers": [
    {
      "key": "draftkings",
      "title": "DraftKings",
      "markets": [
        {
          "key": "player_points",
          "last_update": "2026-10-21T18:00:00Z",
          "outcomes": [
            { "name": "Over", "description": "Stephen Curry", "price": -110, "point": 27.5 },
            { "name": "Under", "description": "Stephen Curry", "price": -110, "point": 27.5 },
            { "name": "Over", "description": "Nikola Jokic", "price": -115, "point": 26.5 },
            { "name": "Under", "description": "Nikola Jokic", "price": -105, "point": 26.5 }
          ]
        },
        {
          "key": "player_rebounds",
          "last_update": "2026-10-21T18:00:00Z",
          "outcomes": [
            { "name": "Over", "description": "Nikola Jokic", "price": -120, "point": 12.5 },
            { "name": "Under", "description": "Nikola Jokic", "price": 100, "point": 12.5 }
          ]
        }
      ]
    }
  ]
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/odds/TheOddsApiClientTests.java`

```java
package com.example.nbaprops.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TheOddsApiClientTests {

    private static final OddsApiProperties PROPERTIES =
            new OddsApiProperties("https://api.the-odds-api.com", "test-key", "basketball_nba", "us");

    private MockRestServiceServer server;
    private TheOddsApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TheOddsApiClient(builder, PROPERTIES);
    }

    @Test
    void getUpcomingEventsParsesTheEventList() {
        server.expect(requestTo("https://api.the-odds-api.com/v4/sports/basketball_nba/events?apiKey=test-key"))
                .andRespond(withSuccess(new ClassPathResource("odds/events.json"), MediaType.APPLICATION_JSON));

        List<OddsEvent> events = client.getUpcomingEvents();

        assertThat(events).extracting(OddsEvent::id).containsExactly("evt-1", "evt-2");
        assertThat(events.getFirst().homeTeam()).isEqualTo("Denver Nuggets");
        assertThat(events.getFirst().commenceTime()).isEqualTo(Instant.parse("2026-10-22T02:00:00Z"));
        assertThat(events.getFirst().bookmakers()).isEmpty();
        server.verify();
    }

    @Test
    void getPlayerPropsParsesBookmakersMarketsAndOutcomes() {
        server.expect(requestTo("https://api.the-odds-api.com/v4/sports/basketball_nba/events/evt-1/odds"
                        + "?apiKey=test-key&regions=us&markets=player_points,player_rebounds&oddsFormat=american"))
                .andRespond(withSuccess(new ClassPathResource("odds/event-odds.json"), MediaType.APPLICATION_JSON));

        OddsEvent odds = client.getPlayerProps("evt-1", List.of("player_points", "player_rebounds"));

        OddsEvent.Bookmaker draftkings = odds.bookmakers().getFirst();
        OddsEvent.Outcome curryOver = draftkings.markets().getFirst().outcomes().getFirst();
        assertThat(draftkings.key()).isEqualTo("draftkings");
        assertThat(curryOver.name()).isEqualTo("Over");
        assertThat(curryOver.description()).isEqualTo("Stephen Curry");
        assertThat(curryOver.price()).isEqualTo(-110);
        assertThat(curryOver.point()).isEqualByComparingTo("27.5");
        server.verify();
    }

    @Test
    void anErrorResponseBecomesAnOddsApiException() {
        server.expect(requestTo(startsWith("https://api.the-odds-api.com/v4/sports/basketball_nba/events")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::getUpcomingEvents)
                .isInstanceOf(OddsApiException.class)
                .hasMessageContaining("401");
    }

    @Test
    void aMissingApiKeyFailsBeforeCallingTheApi() {
        TheOddsApiClient withoutKey = new TheOddsApiClient(RestClient.builder(),
                new OddsApiProperties("https://api.the-odds-api.com", "", "basketball_nba", "us"));

        assertThatThrownBy(withoutKey::getUpcomingEvents)
                .isInstanceOf(OddsApiException.class)
                .hasMessageContaining("ODDS_API_KEY");
    }
}
```

</details>

```powershell
.\mvnw.cmd test -Dtest=TheOddsApiClientTests
```

Expected: `Tests run: 4, Failures: 0`.

### Step 10.5: The import service

**Task:**

1. In `StatType`, give each constant its market key (`POINTS` is `"player_points"`, and so on), a `marketKey()` accessor, and a `static Optional<StatType> fromMarketKey(String)` method.
2. Add `Optional<Player> findByNameIgnoreCase(String name)` to `PlayerRepository`.
3. Add `Optional<PropLine> findByPlayerAndStatTypeAndBookmakerAndGameTime(...)` to `PropLineRepository`. That combination is the *natural key* of an imported line: the same player, stat, and bookmaker, for the same tip-off.
4. Create a record `ImportResult(int events, int created, int updated)`.
5. Create `OddsImportService` with a `@Transactional` method, `ImportResult importPlayerProps()`, that:
   1. asks for the upcoming events, and for each one fetches the props for every `StatType` market;
   2. walks through each bookmaker and each market, skipping markets that don't match a `StatType`, and pairs the Over and Under outcomes for each player;
   3. finds the player by name, creating the player (with no team) if needed;
   4. updates the existing line for that player, stat, bookmaker, and game time, or creates a new one;
   5. counts the events and the created and updated lines.

<details><summary>Hint 1</summary>

Enums can have fields and constructors: declare `POINTS("player_points"), ...;` followed by `private final String marketKey;` and a constructor that sets it.

</details>

<details><summary>Hint 2</summary>

Group the outcomes by player with `Collectors.groupingBy(Outcome::description, LinkedHashMap::new, Collectors.toList())`. `LinkedHashMap` keeps the API's order.

</details>

<details><summary>Hint 3</summary>

Split the work into small private methods: one that turns an `OddsEvent` into a flat list of lines, and one that upserts a single line.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/StatType.java`

```java
package com.example.nbaprops.propline;

import java.util.Arrays;
import java.util.Optional;

public enum StatType {
    POINTS("player_points"),
    REBOUNDS("player_rebounds"),
    ASSISTS("player_assists"),
    THREES("player_threes");

    private final String marketKey;

    StatType(String marketKey) {
        this.marketKey = marketKey;
    }

    public String marketKey() {
        return marketKey;
    }

    public static Optional<StatType> fromMarketKey(String marketKey) {
        return Arrays.stream(values())
                .filter(statType -> statType.marketKey.equals(marketKey))
                .findFirst();
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/player/PlayerRepository.java`

```java
package com.example.nbaprops.player;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeamIgnoreCase(String team);

    boolean existsByNameIgnoreCase(String name);

    Optional<Player> findByNameIgnoreCase(String name);
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/propline/PropLineRepository.java`

```java
package com.example.nbaprops.propline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nbaprops.player.Player;

public interface PropLineRepository extends JpaRepository<PropLine, Long> {

    @Override
    @EntityGraph(attributePaths = "player")
    List<PropLine> findAll();

    @EntityGraph(attributePaths = "player")
    List<PropLine> findByPlayerId(Long playerId);

    @EntityGraph(attributePaths = "player")
    List<PropLine> findByStatType(StatType statType);

    @EntityGraph(attributePaths = "player")
    List<PropLine> findByPlayerIdAndStatType(Long playerId, StatType statType);

    Optional<PropLine> findByPlayerAndStatTypeAndBookmakerAndGameTime(Player player, StatType statType,
            String bookmaker, Instant gameTime);
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/ImportResult.java`

```java
package com.example.nbaprops.odds;

public record ImportResult(int events, int created, int updated) {
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsImportService.java`

```java
package com.example.nbaprops.odds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nbaprops.player.Player;
import com.example.nbaprops.player.PlayerRepository;
import com.example.nbaprops.propline.PropLine;
import com.example.nbaprops.propline.PropLineRepository;
import com.example.nbaprops.propline.StatType;

@Service
public class OddsImportService {

    private final OddsClient oddsClient;
    private final PlayerRepository playerRepository;
    private final PropLineRepository propLineRepository;

    public OddsImportService(OddsClient oddsClient, PlayerRepository playerRepository,
            PropLineRepository propLineRepository) {
        this.oddsClient = oddsClient;
        this.playerRepository = playerRepository;
        this.propLineRepository = propLineRepository;
    }

    @Transactional
    public ImportResult importPlayerProps() {
        List<String> markets = Arrays.stream(StatType.values())
                .map(StatType::marketKey)
                .toList();
        List<OddsEvent> events = oddsClient.getUpcomingEvents();
        int created = 0;
        int updated = 0;
        for (OddsEvent event : events) {
            for (ImportedLine line : toLines(oddsClient.getPlayerProps(event.id(), markets))) {
                if (upsert(line)) {
                    created++;
                } else {
                    updated++;
                }
            }
        }
        return new ImportResult(events.size(), created, updated);
    }

    private static List<ImportedLine> toLines(OddsEvent event) {
        String matchup = event.awayTeam() + " @ " + event.homeTeam();
        List<ImportedLine> lines = new ArrayList<>();
        for (OddsEvent.Bookmaker bookmaker : event.bookmakers()) {
            for (OddsEvent.Market market : bookmaker.markets()) {
                Optional<StatType> statType = StatType.fromMarketKey(market.key());
                if (statType.isEmpty()) {
                    continue;
                }
                Map<String, List<OddsEvent.Outcome>> outcomesByPlayer = market.outcomes().stream()
                        .collect(Collectors.groupingBy(OddsEvent.Outcome::description, LinkedHashMap::new,
                                Collectors.toList()));
                outcomesByPlayer.forEach((playerName, outcomes) -> {
                    Optional<OddsEvent.Outcome> over = find(outcomes, "Over");
                    Optional<OddsEvent.Outcome> under = find(outcomes, "Under");
                    if (over.isPresent() && under.isPresent()) {
                        lines.add(new ImportedLine(playerName, statType.get(), over.get().point(),
                                over.get().price(), under.get().price(), bookmaker.key(), event.commenceTime(),
                                matchup));
                    }
                });
            }
        }
        return lines;
    }

    private static Optional<OddsEvent.Outcome> find(List<OddsEvent.Outcome> outcomes, String name) {
        return outcomes.stream()
                .filter(outcome -> outcome.name().equals(name))
                .findFirst();
    }

    private boolean upsert(ImportedLine line) {
        Player player = playerRepository.findByNameIgnoreCase(line.playerName())
                .orElseGet(() -> playerRepository.save(new Player(line.playerName(), null)));
        Optional<PropLine> existing = propLineRepository.findByPlayerAndStatTypeAndBookmakerAndGameTime(
                player, line.statType(), line.bookmaker(), line.gameTime());
        if (existing.isPresent()) {
            existing.get().update(player, line.statType(), line.line(), line.overOdds(), line.underOdds(),
                    line.bookmaker(), line.gameTime(), line.matchup());
            return false;
        }
        propLineRepository.save(new PropLine(player, line.statType(), line.line(), line.overOdds(),
                line.underOdds(), line.bookmaker(), line.gameTime(), line.matchup()));
        return true;
    }

    private record ImportedLine(String playerName, StatType statType, BigDecimal line, int overOdds,
            int underOdds, String bookmaker, Instant gameTime, String matchup) {
    }
}
```

</details>

### Step 10.6: Test the import with a fake client

**Task:** Write `OddsImportServiceTests`, a `@DataJpaTest` that uses the real repositories on H2. Inside it, write a `FakeOddsClient implements OddsClient` that returns one event with a points market, a rebounds market, and a `player_blocks` market the app doesn't support. Create the service yourself with `new OddsImportService(fakeClient, playerRepository, propLineRepository)`. Test that:

- the first import creates 3 lines and the missing player (Jokic); skips the blocks market, so there's no Wembanyama; and sets the matchup and bookmaker;
- after Curry's line moves to 28.5, a second import updates all 3 lines instead of duplicating them.

<details><summary>Hint 1</summary>

A fake is just a small class that implements the interface. When you need realistic data, it's often simpler than a Mockito mock.

</details>

<details><summary>Hint 2</summary>

Here the service isn't a Spring bean, so its `@Transactional` does nothing. The per-test transaction from `@DataJpaTest` covers it instead.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/odds/OddsImportServiceTests.java`

```java
package com.example.nbaprops.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.nbaprops.odds.OddsEvent.Bookmaker;
import com.example.nbaprops.odds.OddsEvent.Market;
import com.example.nbaprops.odds.OddsEvent.Outcome;
import com.example.nbaprops.player.Player;
import com.example.nbaprops.player.PlayerRepository;
import com.example.nbaprops.propline.PropLine;
import com.example.nbaprops.propline.PropLineRepository;
import com.example.nbaprops.propline.StatType;

@DataJpaTest
class OddsImportServiceTests {

    private static final Instant TIP_OFF = Instant.parse("2026-10-22T02:00:00Z");

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PropLineRepository propLineRepository;

    private final FakeOddsClient oddsClient = new FakeOddsClient();

    private OddsImportService oddsImportService;

    @BeforeEach
    void setUp() {
        oddsImportService = new OddsImportService(oddsClient, playerRepository, propLineRepository);
        playerRepository.save(new Player("Stephen Curry", "Warriors"));
    }

    @Test
    void importCreatesLinesAndAnyMissingPlayers() {
        ImportResult result = oddsImportService.importPlayerProps();

        assertThat(result).isEqualTo(new ImportResult(1, 3, 0));
        assertThat(playerRepository.findByNameIgnoreCase("Nikola Jokic")).isPresent();
        assertThat(playerRepository.findByNameIgnoreCase("Victor Wembanyama")).isEmpty();
        assertThat(propLineRepository.findAll())
                .extracting(PropLine::getMatchup, PropLine::getBookmaker)
                .containsOnly(tuple("Golden State Warriors @ Denver Nuggets", "draftkings"));
    }

    @Test
    void importingAgainUpdatesTheExistingLines() {
        oddsImportService.importPlayerProps();
        oddsClient.curryPoints = new BigDecimal("28.5");

        ImportResult second = oddsImportService.importPlayerProps();

        assertThat(second).isEqualTo(new ImportResult(1, 0, 3));
        assertThat(propLineRepository.findAll()).hasSize(3);
        assertThat(propLineRepository.findByStatType(StatType.POINTS))
                .filteredOn(line -> line.getPlayer().getName().equals("Stephen Curry"))
                .singleElement()
                .satisfies(line -> assertThat(line.getLine()).isEqualByComparingTo("28.5"));
    }

    private static class FakeOddsClient implements OddsClient {

        private BigDecimal curryPoints = new BigDecimal("27.5");

        @Override
        public List<OddsEvent> getUpcomingEvents() {
            return List.of(new OddsEvent("evt-1", TIP_OFF, "Denver Nuggets", "Golden State Warriors", null));
        }

        @Override
        public OddsEvent getPlayerProps(String eventId, List<String> markets) {
            Market points = new Market("player_points", List.of(
                    new Outcome("Over", "Stephen Curry", -110, curryPoints),
                    new Outcome("Under", "Stephen Curry", -110, curryPoints),
                    new Outcome("Over", "Nikola Jokic", -115, new BigDecimal("26.5")),
                    new Outcome("Under", "Nikola Jokic", -105, new BigDecimal("26.5"))));
            Market rebounds = new Market("player_rebounds", List.of(
                    new Outcome("Over", "Nikola Jokic", -120, new BigDecimal("12.5")),
                    new Outcome("Under", "Nikola Jokic", 100, new BigDecimal("12.5"))));
            Market blocks = new Market("player_blocks", List.of(
                    new Outcome("Over", "Victor Wembanyama", -130, new BigDecimal("3.5")),
                    new Outcome("Under", "Victor Wembanyama", 100, new BigDecimal("3.5"))));
            return new OddsEvent(eventId, TIP_OFF, "Denver Nuggets", "Golden State Warriors",
                    List.of(new Bookmaker("draftkings", List.of(points, rebounds, blocks))));
        }
    }
}
```

</details>

### Step 10.7: The admin endpoint

**Task:**

1. Create `OddsImportController`. `POST /api/admin/odds/import` returns the `ImportResult`.
2. In `GlobalExceptionHandler`, map `OddsApiException` to **502 Bad Gateway**.
3. Write `OddsImportControllerTests`, a `@WebMvcTest` that imports `SecurityConfig`:
   - An admin gets 200 with the counts.
   - A plain user gets 403, and the service is never called.
   - An `OddsApiException` becomes a 502.

Your security rules already cover this endpoint: it's a POST under `/api/**`, so only an admin can call it.

<details><summary>Hint</summary>

502 means "I'm acting as a gateway, and the server behind me failed". It's the right status when your app works fine but a service it depends on doesn't.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsImportController.java`

```java
package com.example.nbaprops.odds;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/odds")
public class OddsImportController {

    private final OddsImportService oddsImportService;

    public OddsImportController(OddsImportService oddsImportService) {
        this.oddsImportService = oddsImportService;
    }

    @PostMapping("/import")
    public ImportResult importPlayerProps() {
        return oddsImportService.importPlayerProps();
    }
}
```

**File:** `nba-props/src/main/java/com/example/nbaprops/error/GlobalExceptionHandler.java`

```java
package com.example.nbaprops.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.nbaprops.odds.OddsApiException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OddsApiException.class)
    public ProblemDetail handleOddsApiFailure(OddsApiException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ex.getBody();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/odds/OddsImportControllerTests.java`

```java
package com.example.nbaprops.odds;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nbaprops.config.SecurityConfig;

@WebMvcTest(OddsImportController.class)
@Import(SecurityConfig.class)
class OddsImportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OddsImportService oddsImportService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminCanRunTheImport() throws Exception {
        when(oddsImportService.importPlayerProps()).thenReturn(new ImportResult(2, 5, 1));

        mockMvc.perform(post("/api/admin/odds/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").value(2))
                .andExpect(jsonPath("$.created").value(5))
                .andExpect(jsonPath("$.updated").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void aPlainUserCannotRunTheImport() throws Exception {
        mockMvc.perform(post("/api/admin/odds/import"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(oddsImportService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anOddsApiFailureBecomesA502() throws Exception {
        when(oddsImportService.importPlayerProps())
                .thenThrow(new OddsApiException("The Odds API responded with 401 UNAUTHORIZED"));

        mockMvc.perform(post("/api/admin/odds/import"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The Odds API responded with 401 UNAUTHORIZED"));
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 50, Failures: 0, Errors: 0`.

### Step 10.8: Try it

Start the app without a key, and call the import:

```powershell
curl.exe -i -X POST -u admin:admin-password http://localhost:8080/api/admin/odds/import
```

```text
HTTP/1.1 502
Content-Type: application/problem+json

{"detail":"No API key configured. Set the ODDS_API_KEY environment variable.","instance":"/api/admin/odds/import","status":502,"title":"Bad Gateway"}
```

Now stop the app, set your key in the same terminal, and start it again:

```powershell
$env:ODDS_API_KEY = "paste-your-key-here"
.\mvnw.cmd spring-boot:run
```

In a second terminal:

```powershell
curl.exe -i -X POST -u admin:admin-password http://localhost:8080/api/admin/odds/import
Invoke-RestMethod "http://localhost:8080/api/prop-lines?statType=POINTS" | Format-Table playerName, line, overOdds, underOdds, bookmaker, matchup
```

The import returns counts shaped like `{"events":8,"created":112,"updated":0}` (your numbers will differ), and the table lists real lines next to the seeded ones. Run the import again, and the lines you already have come back as `updated` instead of being duplicated. In the off-season you'll see `"created":0`, which is correct, since there are no player props to import.

If the key is wrong, you get `502` with `"detail":"The Odds API responded with 401 UNAUTHORIZED"`.

Watch your credits. Each import costs up to 4 credits per game (4 markets times 1 region), while listing the events is free. On a 10-game night that's about 40 credits, so the free 500 credits cover roughly a dozen imports a month.

### Quick checks

**1. Why does `OddsImportService` depend on the `OddsClient` interface instead of `TheOddsApiClient`?**

<details><summary>Answer</summary>

So it can be tested and reused without HTTP. The tests pass in a fake with canned data: no network, no key, no credits, and no flakiness. It also makes it easy to switch data providers later: write another implementation, and the service doesn't change. This is the dependency inversion principle, and dependency injection makes it practical.

</details>

**2. When would you use `@Value`, and when `@ConfigurationProperties`?**

<details><summary>Answer</summary>

`@Value` injects one property at a time. It's fine for one or two values, like the passwords in chapter 9. `@ConfigurationProperties` binds a whole group of related properties to a typed object, here a record, with relaxed binding and one obvious place to look. Prefer it for anything with more than a couple of related settings.

</details>

**3. Why return 502 instead of 500 when The Odds API fails?**

<details><summary>Answer</summary>

500 says "this server has a bug". 502 Bad Gateway says "this server is fine, but a server it depends on failed". That's accurate, and it tells clients and operators where to look.

</details>

**4. `importPlayerProps()` is `@Transactional` and calls a remote API inside the transaction. What's the downside, and how would you improve it?**

<details><summary>Answer</summary>

The database transaction, and the pooled connection it holds, stays open while the app waits on the network, possibly for seconds. Under load that can exhaust the connection pool, and locks are held longer. A better design fetches all the data first, outside any transaction, and then writes it in one short transaction (in a separate bean, or with `TransactionTemplate`).

</details>

**5. How do you keep the API key out of git, and how does it reach the app?**

<details><summary>Answer</summary>

`application.properties` only contains the placeholder `${ODDS_API_KEY:}`. The real key is set as an environment variable wherever the app runs: your terminal, a CI secret, or a container's environment. Spring resolves the placeholder at startup.

</details>

**6. What does the `MockRestServiceServer` test check that the fake `OddsClient` doesn't?**

<details><summary>Answer</summary>

The HTTP layer itself: the exact URL and query parameters sent, JSON mapping of realistically shaped responses, and the handling of error status codes. The fake skips all of that so it can test the import logic on its own. Together they cover both halves.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 10: importing real odds with RestClient"
```

## Chapter 11: Actuator and Docker

**Goal:** Make the app observable, with health checks, build info, and metrics. Then ship it as a container that runs next to PostgreSQL with a single command.

**You'll learn:**
- Spring Boot Actuator: exposing and securing its endpoints
- liveness versus readiness
- writing a `HealthIndicator`, and build info
- Docker images and containers, and multi-stage Dockerfiles
- Compose networking, configuration through environment variables, and Compose profiles

### Concept primer

**Actuator** adds production-ready endpoints under `/actuator`:

| Endpoint | What it shows | Who can see it in this app |
|---|---|---|
| `/actuator/health` | `UP` or `DOWN`, plus per-component details | Anyone; the details are admin-only |
| `/actuator/health/liveness` | Is the process alive? | Anyone |
| `/actuator/health/readiness` | Is it ready to serve traffic? | Anyone |
| `/actuator/info` | Build version and time | Anyone |
| `/actuator/metrics` | JVM memory, HTTP request timings, the database connection pool, and more | Admin |

Expose only what you need. Some endpoints reveal internals such as configuration values or memory contents.

**Liveness versus readiness** are terms from Kubernetes, but other orchestrators use the same ideas:

- **Liveness:** is the process broken? If this check fails, the platform *restarts* the container.
- **Readiness:** can the app handle traffic right now? If this check fails, the platform *stops sending requests* to the container but leaves it running. That's what you want while it's starting up, for example.

A **health indicator** reports the status of one component, such as the database. Actuator combines them into the overall status: `DOWN` wins over `UP`, and `UNKNOWN` doesn't pull the overall status down.

**Docker basics:**

- An **image** is a read-only template: your app plus everything it needs to run (operating system files, a JRE, your jar).
- A **container** is a running instance of an image.
- A **multi-stage build** compiles in one stage, which has the full JDK and Maven, then copies only the jar into a smaller stage that has just a JRE. The final image contains no build tools and no source code.

```text
Dockerfile
  stage "build"  (eclipse-temurin:21-jdk):  ./mvnw package  --> target/nba-props-0.0.1-SNAPSHOT.jar
                                                                      |
  final stage    (eclipse-temurin:21-jre):  COPY --from=build --------+-->  java -jar app.jar
```

**Compose** runs several containers on a private network, where each service is reachable by its name. The app connects to `postgres:5432`, not `localhost:5432`, because inside a container `localhost` means the container itself.

### Step 11.1: Add Actuator

**Task:**

1. Add `spring-boot-starter-actuator` to `pom.xml`.
2. In the `spring-boot-maven-plugin` section, add an `<execution>` with the `build-info` goal. It generates `META-INF/build-info.properties`, which `/actuator/info` displays.
3. In `application.properties`:
   - expose `health`, `info`, and `metrics` over HTTP;
   - show health details only to authorized users with the `ADMIN` role;
   - turn on the liveness and readiness probe groups.

<details><summary>Hint</summary>

The property names are:
- `management.endpoints.web.exposure.include`
- `management.endpoint.health.show-details` (use `when-authorized`)
- `management.endpoint.health.roles`
- `management.endpoint.health.probes.enabled`

</details>

<details><summary>Solution</summary>

**Excerpt:** `nba-props/pom.xml`

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

**Excerpt:** `nba-props/pom.xml`

```xml
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>build-info</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

**File:** `nba-props/src/main/resources/application.properties`

```properties
spring.application.name=nba-props

# In-memory H2 database. The fixed name lets you connect to it from the H2 console.
spring.datasource.url=jdbc:h2:mem:nbaprops
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
spring.h2.console.enabled=true

# Passwords for the two built-in users. The defaults are for local development only;
# set USER_PASSWORD and ADMIN_PASSWORD in any shared or deployed environment.
nba-props.security.user-password=${USER_PASSWORD:user-password}
nba-props.security.admin-password=${ADMIN_PASSWORD:admin-password}

# The Odds API (https://the-odds-api.com). Set ODDS_API_KEY to import live lines.
nba-props.odds-api.base-url=https://api.the-odds-api.com
nba-props.odds-api.api-key=${ODDS_API_KEY:}
nba-props.odds-api.sport=basketball_nba
nba-props.odds-api.regions=us

# Actuator: expose only what you need over HTTP, and show health details to admins only.
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
management.endpoint.health.roles=ADMIN
management.endpoint.health.probes.enabled=true
```

</details>

### Step 11.2: Secure the endpoints

**Task:** In `SecurityConfig`, add two rules *before* the `/h2-console` rule: permit `/actuator/health/**` and `/actuator/info`, and require the `ADMIN` role for everything else under `/actuator/**`.

<details><summary>Hint</summary>

The pattern `/actuator/health/**` also matches `/actuator/health` itself.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/config/SecurityConfig.java`

```java
package com.example.nbaprops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder,
            @Value("${nba-props.security.user-password}") String userPassword,
            @Value("${nba-props.security.admin-password}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("user")
                        .password(passwordEncoder.encode(userPassword))
                        .roles("USER")
                        .build(),
                User.withUsername("admin")
                        .password(passwordEncoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

</details>

### Step 11.3: A custom health indicator

**Task:** Create `odds/OddsApiHealthIndicator`, a `@Component` that implements `HealthIndicator`:

- If a key is configured, report `UP` with the detail `apiKeyConfigured=true`.
- Otherwise, report `UNKNOWN` with `apiKeyConfigured=false`.

Don't call the API.

<details><summary>Hint 1</summary>

`Health.up()` and `Health.unknown()` each return a builder. Finish with `.withDetail("apiKeyConfigured", value).build()`.

</details>

<details><summary>Hint 2</summary>

The bean name `oddsApiHealthIndicator` shows up in `/actuator/health` as a component called `oddsApi`.

</details>

<details><summary>Hint 3</summary>

Why not call the API? Health checks run often, every few seconds under an orchestrator. Each check would become a network call to a third party, and a slow or broken external API would make *your* app look unhealthy. An orchestrator might then restart an app that's working perfectly well.

</details>

> **Boot 4 note:** `HealthIndicator` and `Health` moved to `org.springframework.boot.health.contributor`. In Boot 3 they were in `org.springframework.boot.actuate.health`.

<details><summary>Solution</summary>

**File:** `nba-props/src/main/java/com/example/nbaprops/odds/OddsApiHealthIndicator.java`

```java
package com.example.nbaprops.odds;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OddsApiHealthIndicator implements HealthIndicator {

    private final OddsApiProperties properties;

    public OddsApiHealthIndicator(OddsApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        boolean apiKeyConfigured = properties.apiKey() != null && !properties.apiKey().isBlank();
        Health.Builder health = apiKeyConfigured ? Health.up() : Health.unknown();
        return health
                .withDetail("apiKeyConfigured", apiKeyConfigured)
                .build();
    }
}
```

</details>

### Step 11.4: Test it

**Task:**

- `OddsApiHealthIndicatorTests`, a plain unit test: the indicator reports `UP` with a key and `UNKNOWN` without one.
- `ActuatorTests`, with `@SpringBootTest` and `@AutoConfigureMockMvc`:
  - Anonymous health is `UP`, with no `components` field.
  - An admin sees `components.db.status` as `UP`, plus the `oddsApi` component.
  - Liveness and readiness are public, and both `UP`.
  - Info shows `build.artifact` as `nba-props`.
  - Metrics return 401 for anonymous callers, 403 for `user`, and 200 for `admin`.

<details><summary>Hint</summary>

Use `jsonPath("$.components").doesNotExist()` and `.with(httpBasic("admin", "admin-password"))`.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/src/test/java/com/example/nbaprops/odds/OddsApiHealthIndicatorTests.java`

```java
package com.example.nbaprops.odds;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class OddsApiHealthIndicatorTests {

    @Test
    void isUpWhenAnApiKeyIsConfigured() {
        Health health = indicator("some-key").health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("apiKeyConfigured", true);
    }

    @Test
    void isUnknownWithoutAnApiKey() {
        Health health = indicator("").health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("apiKeyConfigured", false);
    }

    private static OddsApiHealthIndicator indicator(String apiKey) {
        return new OddsApiHealthIndicator(
                new OddsApiProperties("https://api.the-odds-api.com", apiKey, "basketball_nba", "us"));
    }
}
```

**File:** `nba-props/src/test/java/com/example/nbaprops/ActuatorTests.java`

```java
package com.example.nbaprops;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anyoneSeesTheOverallHealthButNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void adminsSeeEveryHealthComponent() throws Exception {
        mockMvc.perform(get("/actuator/health").with(httpBasic("admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.oddsApi.details.apiKeyConfigured").exists());
    }

    @Test
    void livenessAndReadinessProbesArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoShowsTheBuild() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("nba-props"));
    }

    @Test
    void metricsNeedAnAdmin() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("user", "user-password")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}
```

</details>

```powershell
.\mvnw.cmd test
```

Expected: `Tests run: 57, Failures: 0, Errors: 0`.

Now start the app and look around:

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/info
curl.exe -u admin:admin-password http://localhost:8080/actuator/metrics/jvm.memory.used
```

```text
{"groups":["liveness","readiness"],"status":"UP"}
{"build":{"artifact":"nba-props","name":"nba-props","time":"...","version":"0.0.1-SNAPSHOT","group":"com.example"}}
{"availableTags":[...],"baseUnit":"bytes","description":"...","measurements":[...],"name":"jvm.memory.used"}
```

### Step 11.5: Package the app as an image

**Task:** Create `nba-props/Dockerfile` with two stages:

1. A `build` stage from `eclipse-temurin:21-jdk`:
   - copy the Maven wrapper (`mvnw` and `.mvn/`) and `pom.xml`;
   - download the dependencies with `./mvnw -B -q dependency:go-offline`;
   - copy `src/`, and run `./mvnw -B -q package -DskipTests`.
2. A final stage from `eclipse-temurin:21-jre`: copy the jar from the build stage as `app.jar`, expose port 8080, and run `java -jar app.jar`.

Also create `nba-props/.dockerignore`, excluding `target/` and IDE folders.

<details><summary>Hint 1</summary>

Copying `pom.xml` before `src/` is a caching trick. Docker reuses the dependency layer as long as `pom.xml` hasn't changed, so rebuilding after a code change doesn't download every dependency again.

</details>

<details><summary>Hint 2</summary>

Run `chmod +x mvnw` first. Files copied from Windows don't always keep their "executable" permission.

</details>

<details><summary>Hint 3</summary>

`-DskipTests` keeps the image build fast. The tests already ran on your machine, and in a real project they'd also run in CI.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/Dockerfile`

```dockerfile
# Stage 1: build the jar with the Maven wrapper and a full JDK.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Stage 2: run the jar on a smaller image that only has a JRE.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/nba-props-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**File:** `nba-props/.dockerignore`

```text
target/
.mvn/wrapper/maven-wrapper.jar
.idea/
.vscode/
*.iml
```

</details>

Build the image:

```powershell
docker build -t nba-props .
docker images nba-props
```

The build output ends with `naming to docker.io/library/nba-props:latest done`. The first build downloads the base images and every dependency, and later builds are much faster.

### Step 11.6: Run the whole stack with Compose

**Task:** Extend `compose.yaml`:

- Give `postgres` a healthcheck: `pg_isready -U nbaprops -d nbaprops`.
- Add an `app` service that:
  - builds from `.` and publishes port 8080;
  - activates the `postgres` Spring profile;
  - sets `SPRING_DATASOURCE_URL` to `jdbc:postgresql://postgres:5432/nbaprops`;
  - passes `POSTGRES_PASSWORD`, `ADMIN_PASSWORD`, and `ODDS_API_KEY` through;
  - waits until `postgres` is healthy.
- Put `app` in a Compose profile called `app`, so that `docker compose up -d` from chapter 8 still starts only the database.

<details><summary>Hint 1</summary>

Inside the Compose network, the database's host name is its service name, `postgres`.

</details>

<details><summary>Hint 2</summary>

`SPRING_DATASOURCE_URL` overrides `spring.datasource.url` from `application-postgres.properties`, because environment variables beat profile files (chapter 8).

</details>

<details><summary>Hint 3</summary>

`depends_on` with `condition: service_healthy` waits for the healthcheck to pass, not just for the container to start.

</details>

<details><summary>Solution</summary>

**File:** `nba-props/compose.yaml`

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: nbaprops
      POSTGRES_USER: nbaprops
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdev}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nbaprops -d nbaprops"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    profiles: ["app"]
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: postgres
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/nbaprops
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdev}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:-admin-password}
      ODDS_API_KEY: ${ODDS_API_KEY:-}
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

</details>

Stop any app you're running with `spring-boot:run`, because Docker needs port 8080. Then:

```powershell
docker compose --profile app up -d --build
docker compose --profile app ps
docker compose logs app
```

In the logs, look for `The following 1 profile is active: "postgres"`, then `Seeded 4 players and 6 prop lines`. If your chapter 8 database already has data, there's no seed line.

Then check the running stack:

```powershell
curl.exe http://localhost:8080/actuator/health/readiness
curl.exe -u admin:admin-password http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/prop-lines | Format-Table id, playerName, statType, line
```

The admin health response shows the database is PostgreSQL, and the Odds API check is `UNKNOWN` while the overall status stays `UP` (trimmed):

```text
{"components":{"db":{"details":{"database":"PostgreSQL","validationQuery":"isValid()"},"status":"UP"},
 "oddsApi":{"details":{"apiKeyConfigured":false},"status":"UNKNOWN"}, ...},"groups":["liveness","readiness"],"status":"UP"}
```

To pass your Odds API key into the container, set `$env:ODDS_API_KEY` in the same terminal before `docker compose --profile app up -d`. Compose fills `${ODDS_API_KEY:-}` from your shell's environment.

Stop everything with `docker compose --profile app down`. Add `-v` to delete the database volume as well.

### Quick checks

**1. Why not expose every Actuator endpoint?**

<details><summary>Answer</summary>

Some endpoints reveal sensitive internals. `env` shows configuration, which may include secrets; `heapdump` dumps memory; `loggers` can change log levels. Expose only what you need over HTTP, and put everything beyond basic health behind authentication.

</details>

**2. What's the difference between liveness and readiness?**

<details><summary>Answer</summary>

Liveness asks whether the app is broken beyond recovery; if it fails, the container is restarted. Readiness asks whether the app can serve traffic right now; if it fails, requests stop coming, but the container keeps running. That's useful while the app is starting up, or when a critical dependency is down.

</details>

**3. Why report `UNKNOWN` instead of `DOWN` when there's no API key?**

<details><summary>Answer</summary>

`DOWN` would make the whole app's health `DOWN`, and an orchestrator might restart or remove a working app just because an optional feature isn't configured. `UNKNOWN` reports the fact without dragging the overall status down.

</details>

**4. What does a multi-stage Dockerfile buy you?**

<details><summary>Answer</summary>

The final image contains only what the app needs to run (a JRE and the jar), not the JDK, Maven, the source code, or build caches. It's smaller, faster to ship, and has less for an attacker to use. Spring Boot can also build images without a Dockerfile at all, with the `spring-boot:build-image` goal, which uses Cloud Native Buildpacks.

</details>

**5. Inside Compose, why does the app connect to `postgres:5432` instead of `localhost:5432`?**

<details><summary>Answer</summary>

Each container has its own network, so inside the app container `localhost` is the app container itself. Compose puts all the services on a shared network, where built-in DNS makes each one reachable by its service name.

</details>

**6. Why copy `pom.xml` and download dependencies before copying `src/`?**

<details><summary>Answer</summary>

Docker caches each layer, and reuses it when that layer's inputs haven't changed. Dependencies change rarely and source code changes often. With this order, a code change only rebuilds the last few layers, instead of downloading every dependency again.

</details>

### Checkpoint

```powershell
git add .
git commit -m "Chapter 11: Actuator and Docker"
```

### You're done

You've built, tested, secured, and containerized a Spring Boot API. Before your interview, go back through the quick checks and answer them out loud without peeking. If you want to keep going, natural next steps are:

- **Flyway** migrations instead of `ddl-auto=update`
- **Testcontainers**, so the tests run against real PostgreSQL
- **pagination** with `Pageable`
- **JWT** authentication instead of HTTP Basic
- **caching** Odds API responses
- deploying the container to a cloud platform

## Appendix A: Annotation cheat sheet

| Annotation | What it does | Chapter |
|---|---|---|
| `@SpringBootApplication` | `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan` | 0 |
| `@Component`, `@Service`, `@Repository` | Register a class as a bean; the name documents its layer | 2 |
| `@Configuration`, `@Bean` | A class whose `@Bean` methods create beans | 9 |
| `@Value("${...}")` | Inject a single property value | 9 |
| `@ConfigurationProperties`, `@ConfigurationPropertiesScan` | Bind a group of properties to a typed object | 10 |
| `@RestController` | `@Controller` + `@ResponseBody`: return values become the response body | 1 |
| `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | Map HTTP method and path to a handler method | 1, 3, 6 |
| `@PathVariable`, `@RequestParam`, `@RequestBody` | Bind a URL segment, a query parameter, or the JSON body | 3 |
| `@ResponseStatus` | Set the response status for a handler method | 6 |
| `@Valid`, `@NotBlank`, `@NotNull`, `@Size`, `@DecimalMin`, `@Digits` | Bean Validation | 5, 6 |
| `@RestControllerAdvice`, `@ExceptionHandler` | Global exception-to-HTTP translation | 5 |
| `@Entity`, `@Id`, `@GeneratedValue`, `@Column` | Map a class to a table | 4 |
| `@ManyToOne`, `@JoinColumn` | A foreign-key relationship | 6 |
| `@Enumerated(EnumType.STRING)` | Store an enum by name | 6 |
| `@EntityGraph` | Fetch associations in the same query (N+1 fix) | 6 |
| `@Transactional` | Run a method in a transaction (via a proxy) | 6 |
| `@JsonProperty`, `@JsonIgnoreProperties` | Control how Jackson maps JSON fields | 10 |
| `@SpringBootTest` | Start the whole application in a test | 0, 7 |
| `@WebMvcTest` | Web-layer slice test | 1 |
| `@DataJpaTest` | JPA slice test, rolled back after each test | 4 |
| `@AutoConfigureMockMvc`, `@AutoConfigureRestTestClient` | Provide `MockMvc` or `RestTestClient` in a `@SpringBootTest` | 7 |
| `@MockitoBean` | Replace a bean in the test context with a Mockito mock | 2 |
| `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` | Mockito without Spring | 4 |
| `@WithMockUser` | Run a test as a fake authenticated user | 9 |
| `@Import` | Add a configuration class to a slice test | 9 |

## Appendix B: Boot 3 vs Boot 4 in material you find online

Most tutorials, Stack Overflow answers, and AI answers were written for Spring Boot 2 or 3. These are the differences you'll run into with this project. Each one was checked against Boot 4.1.1.

| What you'll see online (Boot 2/3) | What works here (Boot 4.1) |
|---|---|
| `spring-boot-starter-web` | Spring Initializr now generates `spring-boot-starter-webmvc` (the old starter still exists) |
| One `spring-boot-starter-test` for everything | Per-technology test starters: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, ... |
| `@MockBean`, `@SpyBean` | Removed. Use `@MockitoBean`, `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito` |
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (same for `AutoConfigureMockMvc`) |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`; `TestEntityManager` is in `org.springframework.boot.jpa.test.autoconfigure` |
| `TestRestTemplate`, always available | `org.springframework.boot.resttestclient.TestRestTemplate` with `@AutoConfigureTestRestTemplate`, or the new `RestTestClient` with `@AutoConfigureRestTestClient` |
| `RestClient.Builder` comes with the web starter | Add `spring-boot-starter-restclient` |
| H2 console built in | Add `spring-boot-h2console` |
| `org.springframework.boot.actuate.health.HealthIndicator` | `org.springframework.boot.health.contributor.HealthIndicator` (and `Health`, `Status`) |
| Jackson 2: `com.fasterxml.jackson.databind.ObjectMapper` | Jackson 3: `tools.jackson.databind.ObjectMapper` / `tools.jackson.databind.json.JsonMapper`. Annotations are unchanged: `com.fasterxml.jackson.annotation.*` |
| JUnit 5 | JUnit 6, with the same `org.junit.jupiter.api` annotations |
| `WWW-Authenticate: Basic realm="Realm"` | Spring Security 7 adds a charset: `Basic realm="Realm", charset="UTF-8"` |

## Appendix C: Troubleshooting

**`java -version` shows the wrong version, or the build says "release version 21 not supported".** Point `JAVA_HOME` at JDK 21. For the current terminal: `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'`. To make it permanent, change it in Windows' environment variable settings.

**`Web server failed to start. Port 8080 was already in use.`** Another app is using the port. It might be an earlier `spring-boot:run` or the Docker `app` container. Find and stop it:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object OwningProcess
Stop-Process -Id <the number from above>
```

Or run on a different port with `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=9090"`.

**`curl` prompts for parameters, or prints a table instead of JSON.** In Windows PowerShell, `curl` is an alias for `Invoke-WebRequest`. Type `curl.exe`.

**The server says your JSON is malformed when you send it with `curl.exe`.** PowerShell 5.1 strips double quotes from arguments to native programs. Pipe the JSON in instead, `'{"name": "x"}' | curl.exe -H "Content-Type: application/json" --data-binary '@-' <url>`, or use `Invoke-RestMethod ... -Body '{"name": "x"}'`.

**Maven ignores or mangles a `-D` option.** Put the whole argument in quotes. PowerShell splits unquoted arguments at dots and treats commas as array separators: write `"-Dspring-boot.run.profiles=postgres"` and `"-Dtest=PlayerRepositoryTests,PlayerServiceTests"`.

**Warnings in the test output: `Mockito is currently self-attaching...`, `A Java agent has been loaded dynamically`, `Sharing is only supported for boot loader classes`.** These are harmless JVM notices about how Mockito installs itself. The tests are fine as long as the output says `BUILD SUCCESS`.

**Strange compile errors about classes you deleted, or old behavior after switching branches.** Stale classes are still in `target/`. Run `.\mvnw.cmd clean test`.

**VS Code shows red errors, but `.\mvnw.cmd test` passes.** The editor hasn't caught up with `pom.xml` changes. Run **Java: Reload Projects** from the command palette (`Ctrl+Shift+P`). If that doesn't help, run **Java: Clean Java Language Server Workspace**.

**`error during connect: ... dockerDesktopLinuxEngine: The system cannot find the file specified.`** Docker Desktop isn't running. Start it, wait until it reports that it's running, and try again.

**`FATAL: password authentication failed for user "nbaprops"`.** Either `POSTGRES_PASSWORD` is set to something else in your terminal (clear it with `Remove-Item Env:POSTGRES_PASSWORD`), or the database volume was created with a different password. To start over with an empty database, run `docker compose down -v`.

**401 on POST, PUT, or DELETE from chapter 9 onward.** Writes need credentials: `curl.exe -u admin:admin-password ...`. A 403 means you're logged in, but not as an admin.

**The H2 console won't connect.** The app must be running, and the JDBC URL must be exactly `jdbc:h2:mem:nbaprops`, with user `sa` and an empty password. With the `postgres` profile active, the console is turned off.
