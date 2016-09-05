NAME:=autumn
VERSION:=0.1.0
KOTLIN_VER:=1.0.3
TESTNG_VER:=6.9.10
JCOMMA_VER:=1.48
DOKKA_VER:=0.9.8
VIOLIN_VER:=0.1.0

TESTNG:="http://central.maven.org/maven2/org/testng/testng/$(TESTNG_VER)/testng-$(TESTNG_VER).jar"
JCOMMA:="http://central.maven.org/maven2/com/beust/jcommander/$(JCOMMA_VER)/jcommander-$(JCOMMA_VER).jar"
DOKKA:="https://github.com/Kotlin/dokka/releases/download/$(DOKKA_VER)/dokka-fatjar.jar"
KOTLIN:="https://github.com/JetBrains/kotlin/releases/download/v$(KOTLIN_VER)/kotlin-compiler-$(KOTLIN_VER).zip"
VIOLIN:="https://dl.bintray.com/norswap/maven/com/norswap/violin/$(VIOLIN_VER)/violin-$(VIOLIN_VER).jar"
VIOLIN_SRC:="https://dl.bintray.com/norswap/maven/com/norswap/violin/$(VIOLIN_VER)/violin-$(VIOLIN_VER)-sources.jar"

ifeq ($(shell if [ -d kotlinc ]; then echo "yes"; fi),yes)
	KOTLINC:=kotlinc/bin/kotlinc
else
	KOTLINC:=kotlinc
endif

ifeq ($(OS),Windows_NT)
	SEP:=;
else
	SEP:=:
endif

# defines BINTRAY_XXX and SONATYPE_XXX
-include local.mk

space:=$(eval) $(eval)
# java supports "lib/*", but not kotlin
jars:=$(subst $(space),$(SEP),$(shell find lib -name "*.jar"))
basecp:=out/production$(SEP)$(jars)
cp:="$(basecp)"
testcp:="$(basecp)$(SEP)out/test"

build:
	mkdir -p out/production
	javac -cp $(cp) -d out/production src/norswap/autumn/utils/JUtils.java
	$(KOTLINC) -cp $(cp) src -d out/production

build-examples:
	mkdir -p out/examples
	$(KOTLINC) -cp $(cp) example -d out/examples

run-examply:
	java -cp "$(basecp)$(SEP)out/examples" examply.MainKt

build-tests:
	mkdir -p out/test
	$(KOTLINC) -cp $(testcp) test -d out/test

test:
	java -cp $(testcp) org.testng.TestNG test/testng.xml -d out/test-output

rebuild: build buildtests test

clean:
	rm -rf out

kotlin:
	curl -L $(KOTLIN) > compiler.zip
	unzip compiler.zip -d .
	rm compiler.zip

clean-kotlin:
	rm -rf kotlinc

deps:
	mkdir -p lib
	curl -L $(VIOLIN)     > lib/violin.jar
	curl -L $(VIOLIN_SRC) > lib/violin-sources.jar
	curl -L $(DOKKA)	  > lib/dokka.jar
	curl -L $(TESTNG)	  > lib/testng.jar
	curl -L $(JCOMMA)     > lib/jcommander.jar

clean-deps:
# leave the jars added by IntelliJ
	find lib ! -name 'kotlin-*.jar' -type f -exec rm -f {} +

jar:
	find out -name .DS_Store -type f -delete
	jar cf out/$(NAME)-$(VERSION).jar -C out/production .

fatjar:
	find out -name .DS_Store -type f -delete
	mkdir -p out/fatjar_staging
	unzip lib/violin.jar -d out/fatjar_staging
	mv out/fatjar_staging/META-INF/main.kotlin_module out/fatjar_staging/META-INF/violin.kotlin_module
	cp -R out/production/* out/fatjar_staging
	jar cf out/$(NAME)-$(VERSION)-fat.jar -C out/fatjar_staging .
	rm -rf out/fatjar_staging

jars: jar
	find src -name .DS_Store -type f -delete
	jar cf out/$(NAME)-$(VERSION)-sources.jar -C src .
	jar cf out/$(NAME)-$(VERSION)-javadoc.jar -C out/docs/java .
	jar cf out/$(NAME)-$(VERSION)-kdoc.jar -C out/docs/kotlin .

BINTRAY_PATH:=https://api.bintray.com/content/norswap/maven/$(NAME)/$(VERSION)
BINTRAY_PATH:=$(BINTRAY_PATH)/com/norswap/$(NAME)/$(VERSION)

binup = curl -T out/$(NAME)-$(VERSION)$(1) -u$(BINTRAY_USER):$(BINTRAY_API_KEY) \
		"$(BINTRAY_PATH)/$(NAME)-$(VERSION)$(1);publish=1;override=1" ; echo "\n"

sign = gpg2 --yes -u 3BC67092 -ab out/$(NAME)-$(VERSION)$(1)

publish:
	sed "s/VERSION/$(VERSION)/g" $(NAME).pom > out/$(NAME)-$(VERSION).pom
	$(call binup,.pom)
	$(call binup,.jar)
	$(call binup,-sources.jar)
	$(call binup,-javadoc.jar)
	$(call binup,-kdoc.jar)
	$(call sign,.pom)
	$(call sign,.jar)
	$(call sign,-sources.jar)
	$(call sign,-javadoc.jar)
	$(call sign,-kdoc.jar)
	$(call binup,.pom.asc)
	$(call binup,.jar.asc)
	$(call binup,-sources.jar.asc)
	$(call binup,-javadoc.jar.asc)
	$(call binup,-kdoc.jar.asc)
	curl -d "username=$(SONATYPE_USER_TOKEN)&password=$(SONATYPE_PWD_TOKEN)" \
		-u$(BINTRAY_USER):$(BINTRAY_API_KEY) \
		https://api.bintray.com/maven_central_sync/content/norswap/maven/$(NAME)/$(VERSION)

docs:
	mkdir -p out/docs/java
	mkdir -p out/docs/kotlin
	java -jar lib/dokka.jar src -output out/docs/kotlin -classpath $(cp) \
		-include src/norswap/$(NAME)/stream/package.md
#	java -cp "$(JAVA_HOME)/lib/tools.jar$(SEP)lib/dokka.jar" org.jetbrains.dokka.MainKt src \
#		-output out/docs/java -format javadoc -classpath $(cp)

pubdocs:
	rm -rf pages/*
	cp $(NAME).html pages/index.html
	cp -R out/docs/java pages/java
	cp -R out/docs/kotlin pages/kotlin
	cd pages ; git add -A . ; git commit -m "update" ; git push origin gh-pages

trace:
	java -cp $(classpath) -agentlib:hprof=cpu=samples,interval=1 $(target)

.PHONY: \
  build \
  build-examples \
  run-examply \
  build-tests \
  test \
  rebuild \
  clean \
  kotlin \
  clean-kotlin \
  deps \
  clean-deps \
  jar \
  fatjar \
  publish \
  docs \
  pubdocs \
  trace

.SILENT:
