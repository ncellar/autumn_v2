target?=ScratchKt
dokka?=../_tools/dokka-fatjar.jar

ifeq ($(OS),Windows_NT)
	SEP:=;
else
	SEP:=:
endif

ifneq ($(debug),)
	override debug=-g
endif

ifneq ($(opti),)
	override debug:=-g:none
endif

space:=$(eval) $(eval)
# java supports "lib/*", but not kotlin
jars:=$(subst $(space),$(SEP),$(shell find lib -name *.jar))
classpath:="output$(SEP)output/resources$(SEP)$(jars)"

build:
	mkdir -p output generated output/resources
	if [ -d resources ]; then cp -R resources/. output; fi
	kotlinc -no-stdlib -cp $(classpath) src test -d output
	javac -Xlint:unchecked $(debug) -d output -s generated \
		-cp $(classpath) `find src test $(shell ls srclib) -name *.java`

clean:
	rm -rf output generated

run:
	java -cp $(classpath) $(agents) $(target)

doc:
	java -jar $(dokka) src -output output/doc -classpath $(classpath)


trace:
	java -cp $(classpath) -agentlib:hprof=cpu=samples,interval=1 $(target)

.PHONY: \
	build \
	clean \
	run \
	trace

.SILENT:
