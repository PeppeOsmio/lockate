# Testing in Java + Spring Boot

## Test Types

### Unit Tests
Test a single class or method in isolation. Dependencies are replaced with mocks.

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldReturnUserById() {
        User user = new User(1L, "Alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getName()).isEqualTo("Alice");
        verify(userRepository).findById(1L);
    }
}
```

- `@ExtendWith(MockitoExtension.class)` — activates Mockito without loading Spring context
- `@Mock` — creates a mock of the dependency
- `@InjectMocks` — creates the class under test and injects mocks into it
- `when(...).thenReturn(...)` — stubs method calls on mocks
- `verify(...)` — asserts a mock method was called

### Integration Tests
Test multiple layers together (e.g., service + repository + database). Spring context is loaded.

```java
@SpringBootTest
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPersistAndRetrieveUser() {
        userRepository.save(new User(null, "Bob"));

        User found = userService.findByName("Bob");

        assertThat(found).isNotNull();
    }
}
```

- `@SpringBootTest` — loads the full application context
- `@Transactional` — rolls back DB changes after each test, keeping tests isolated

### Web Layer (Controller) Tests
Test only the HTTP layer. The service is mocked.

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void shouldReturn200WithUser() throws Exception {
        when(userService.findById(1L)).thenReturn(new User(1L, "Alice"));

        mockMvc.perform(get("/users/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("Alice"));
    }
}
```

- `@WebMvcTest` — loads only the web layer (controllers, filters), not the full context
- `@MockBean` — adds a Mockito mock as a Spring bean, replacing the real one
- `MockMvc` — simulates HTTP requests without starting a real server

### Repository Tests
Test JPA repositories against a real (in-memory) database.

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindByName() {
        userRepository.save(new User(null, "Carol"));

        Optional<User> found = userRepository.findByName("Carol");

        assertThat(found).isPresent();
    }
}
```

- `@DataJpaTest` — loads only JPA context, uses H2 in-memory DB by default, rolls back after each test

---

## Key Annotations Summary

| Annotation | Scope | Loads Spring? |
|---|---|---|
| `@ExtendWith(MockitoExtension.class)` | Unit | No |
| `@SpringBootTest` | Integration/E2E | Yes (full) |
| `@WebMvcTest` | Controller | Yes (web only) |
| `@DataJpaTest` | Repository | Yes (JPA only) |
| `@MockBean` | Any Spring test | Replaces bean in context |
| `@Mock` | Mockito only | No |

---

## Test Slices vs Full Context

Spring Boot provides **test slices** — partial context loads that are faster than `@SpringBootTest`:

- `@WebMvcTest` — controllers only
- `@DataJpaTest` — JPA only
- `@JsonTest` — JSON serialization only
- `@RestClientTest` — REST clients only

Use slices when you only need to test one layer. Use `@SpringBootTest` when you need the full wiring.

---

## AssertJ (Fluent Assertions)

Spring Boot tests default to AssertJ over JUnit's plain `assertEquals`:

```java
assertThat(result).isNotNull();
assertThat(list).hasSize(3).contains("Alice");
assertThat(value).isGreaterThan(0).isLessThan(100);
assertThatThrownBy(() -> service.doThing())
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("invalid");
```

---

## Mockito Essentials

```java
// Stub a return value
when(mock.method(arg)).thenReturn(value);

// Stub to throw
when(mock.method(arg)).thenThrow(new RuntimeException());

// Verify a call happened
verify(mock).method(arg);
verify(mock, times(2)).method(any());
verify(mock, never()).method(any());

// Argument matchers
when(repo.findById(anyLong())).thenReturn(Optional.of(user));
when(repo.findByName(eq("Alice"))).thenReturn(Optional.of(user));

// Capture arguments
ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
verify(repo).save(captor.capture());
assertThat(captor.getValue().getName()).isEqualTo("Alice");
```

---

## Test Lifecycle

```java
@BeforeAll   // runs once before all tests in the class (must be static)
@BeforeEach  // runs before each test
@AfterEach   // runs after each test
@AfterAll    // runs once after all tests (must be static)
```

---

## Common Pitfalls

- **Slow tests**: avoid `@SpringBootTest` when a slice or plain Mockito test suffices
- **Shared state**: always reset mocks/data between tests; `@Transactional` or `@BeforeEach` cleanup help
- **Testing implementation details**: prefer testing behavior (outputs/side effects) over internal calls
- **H2 vs real DB**: `@DataJpaTest` uses H2 by default; add `@AutoConfigureTestDatabase(replace = NONE)` to use the real DB
- **Missing `@MockBean`**: if a `@SpringBootTest` test fails because a bean can't be autowired, you need `@MockBean` for unused dependencies
