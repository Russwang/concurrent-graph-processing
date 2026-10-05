JAVAC ?= javac
JAVA ?= java
JFLAGS ?= -g -Xlint:all
CLASS_DIR = build/classes
TEST_DIR = build/test-classes
SOURCES = Driver.java $(wildcard uk/ac/qub/csc3021/graph/*.java)
TEST_SOURCES = $(wildcard tests/*.java)
PACKAGE = lib/graph.jar

.PHONY: all test clean submit
all: $(PACKAGE)

build/.compiled: $(SOURCES)
	mkdir -p $(CLASS_DIR)
	$(JAVAC) $(JFLAGS) -d $(CLASS_DIR) $(SOURCES)
	touch $@

$(PACKAGE): build/.compiled
	mkdir -p lib
	jar cfe $@ Driver -C $(CLASS_DIR) .

test: all
	mkdir -p $(TEST_DIR)
	$(JAVAC) $(JFLAGS) -cp $(CLASS_DIR) -d $(TEST_DIR) $(TEST_SOURCES)
	$(JAVA) -ea -cp $(CLASS_DIR):$(TEST_DIR) uk.ac.qub.csc3021.graph.GraphRegressionTest
	python3 tests/test_cli.py

submit: submit.zip
submit.zip: $(SOURCES)
	$(RM) $@
	zip $@ $(SOURCES)

clean:
	$(RM) -r build lib
	$(RM) Driver.class uk/ac/qub/csc3021/graph/*.class submit.zip
