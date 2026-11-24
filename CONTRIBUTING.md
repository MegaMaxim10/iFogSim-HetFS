# Contributing to iFogSim-HetFS

Thank you for your interest in contributing to iFogSim-HetFS! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

We are committed to providing a welcoming and inspiring community for all. Please be respectful and constructive in all interactions.

## How to Contribute

### Reporting Bugs

If you discover a bug, please open an issue with:
- A clear, descriptive title
- A detailed description of the problem
- Steps to reproduce the issue
- Expected behavior vs. actual behavior
- Your Java and Maven versions
- Any relevant code snippets or stack traces

### Suggesting Features

Feature suggestions are welcome! Please open an issue with:
- A clear, descriptive title
- A detailed description of the proposed feature
- Use cases and examples of how it would be used
- Any alternative approaches you've considered

### Submitting Code

1. **Fork the repository** and create a feature branch:
   ```bash
   git checkout -b feature/my-feature
   ```

2. **Follow the code style**:
   - Java 8 compatible syntax
   - Comprehensive Javadoc comments on all public methods
   - Clear variable and method names
   - No trailing whitespace
   - 4-space indentation

3. **Add tests** for new functionality:
   - Write JUnit 4 tests in `src/test/java/`
   - Test both happy path and error cases
   - Aim for >80% code coverage on new code

4. **Document your changes**:
   - Update README.md if needed
   - Add Javadoc to all public classes and methods
   - Update CHANGELOG.md with your changes

5. **Commit with clear messages**:
   ```bash
   git commit -m "Add feature: description of changes"
   ```

6. **Push to your fork** and submit a pull request:
   - Describe what the PR addresses
   - Link to any related issues
   - Ensure CI/CD tests pass

## Development Setup

### Prerequisites
- Java 8 or later
- Maven 3.6 or later
- Git

### Building Locally

```bash
# Clone the repository
git clone https://github.com/nafasi-digital/ifogsim-hetfs.git
cd ifogsim-hetfs

# Build the project
mvn clean package

# Run tests
mvn test

# Run examples
mvn exec:java -Dexec.mainClass="org.fog.hetfs.examples.ArbitraryTopologyExample"
```

### IDE Setup

#### IntelliJ IDEA
1. Open project > pom.xml as project
2. Maven should automatically configure
3. Mark `src/main/java` as Sources Root
4. Mark `src/test/java` as Test Sources Root

#### Eclipse
1. Import as Existing Maven Project
2. Maven nature should be detected automatically

## Module-Specific Guidelines

### D1: Topology
- Ensure all graph operations are correctly implemented
- Test Dijkstra with various topologies
- Validate routing table consistency

### D2: Hardware
- Add new compute architectures carefully (consider parallelism multipliers)
- Validate power model assumptions
- Document any model simplifications

### D3: Concurrent Processing
- Ensure thread-safe queue operations
- Test with high concurrent stream counts
- Validate statistics accuracy

### D4: Stateful Modules
- Test checkpoint/restore serialization
- Validate state consistency across recovery
- Document lifecycle hook requirements

### D5: Events
- Test topology changes don't break routing
- Validate event ordering and timing
- Document side effects of topology mutations

### D6: Timing
- Use Welford's algorithm for online stats
- Validate confidence interval calculations
- Test with various sample sizes

### D7: Energy
- Validate battery model physics
- Test edge cases (exhaustion, recharge)
- Document energy unit conversions

## Testing Standards

- All public APIs must have unit tests
- Use descriptive test method names
- Test both success and failure cases
- Use meaningful assertion messages

```java
@Test
public void shouldComputeShortestPathBetweenConnectedDevices() {
    // Arrange
    PhysicalGraph graph = new PhysicalGraph();
    graph.addDevice(0);
    graph.addDevice(1);
    graph.addLink(0, 1, 10.0, 1000.0);

    // Act
    List<Integer> path = graph.getShortestPath(0, 1);

    // Assert
    assertEquals(2, path.size());
    assertEquals(0, path.get(0).intValue());
    assertEquals(1, path.get(1).intValue());
}
```

## Pull Request Process

1. Update CHANGELOG.md with your changes
2. Ensure all tests pass: `mvn test`
3. Ensure no style violations: `mvn checkstyle:check`
4. Create pull request with clear description
5. Address reviewer feedback promptly

## Release Process

Only maintainers can cut releases:

```bash
# Update version in pom.xml
mvn versions:set -DnewVersion=X.Y.Z

# Build and test
mvn clean test package

# Tag release
git tag -a vX.Y.Z -m "Release version X.Y.Z"
git push origin vX.Y.Z

# Deploy to Maven Central
mvn deploy
```

## License

By contributing to iFogSim-HetFS, you agree that your contributions will be licensed under the Apache License 2.0.

## Questions?

Feel free to reach out:
- Open a discussion issue
- Contact: support@nafasi-digital.com
- Check existing documentation and examples

Thank you for helping improve iFogSim-HetFS!
