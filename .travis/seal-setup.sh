# Sealed build environment setup, sourced by .travis.yml (before_install) and by
# .github/workflows/seal-build.yml. Do not use `set -e`: this file is sourced.
#
# 1. Maven 3.6.1 - the version that built the published jar (Created-By header).
# 2. ~/.m2/settings.xml mirroring every repository to Maven Central: the poms
#    declare repo.spring.io repositories, which now answer 401 to anonymous users.
# 3. A local copy of the parent spring-data-parent 1.9.23.RELEASE with its
#    com.springsource.bundlor.maven plugin removed: that plugin (1.0.0.RELEASE)
#    was only ever published to repo.spring.io and is no longer downloadable,
#    and the parent binds it into every build. The jar plugin still packages
#    ${manifest.file} (target/classes/META-INF/MANIFEST.MF), which now comes from
#    src/main/resources/META-INF/MANIFEST.MF: the OSGi manifest bundlor produced
#    for the published 1.13.23.RELEASE jar.
SEAL_MAVEN_VERSION=3.6.1
SEAL_PARENT_VERSION=1.9.23.RELEASE
SEAL_CENTRAL=https://repo.maven.apache.org/maven2

for URL in "$SEAL_CENTRAL/org/apache/maven/apache-maven/$SEAL_MAVEN_VERSION/apache-maven-$SEAL_MAVEN_VERSION-bin.tar.gz" \
           "https://archive.apache.org/dist/maven/maven-3/$SEAL_MAVEN_VERSION/binaries/apache-maven-$SEAL_MAVEN_VERSION-bin.tar.gz"; do
  curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-maven.tar.gz "$URL" && tar -xzf /tmp/seal-maven.tar.gz -C /tmp && break
done
[ -x "/tmp/apache-maven-$SEAL_MAVEN_VERSION/bin/mvn" ] || return 1
export M2_HOME="/tmp/apache-maven-$SEAL_MAVEN_VERSION"
export PATH="$M2_HOME/bin:$PATH"

mkdir -p "$HOME/.m2" || return 1
cat > "$HOME/.m2/settings.xml" <<EOF
<settings>
  <mirrors>
    <mirror>
      <id>seal-central</id>
      <mirrorOf>*</mirrorOf>
      <url>$SEAL_CENTRAL</url>
    </mirror>
  </mirrors>
</settings>
EOF

SEAL_PARENT_DIR="$HOME/.m2/repository/org/springframework/data/build/spring-data-parent/$SEAL_PARENT_VERSION"
SEAL_PARENT_POM="$SEAL_PARENT_DIR/spring-data-parent-$SEAL_PARENT_VERSION.pom"
mkdir -p "$SEAL_PARENT_DIR" || return 1
rm -f "$SEAL_PARENT_DIR"/_remote.repositories "$SEAL_PARENT_DIR"/*.sha1 "$SEAL_PARENT_DIR"/*.lastUpdated
curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-parent.pom \
  "$SEAL_CENTRAL/org/springframework/data/build/spring-data-parent/$SEAL_PARENT_VERSION/spring-data-parent-$SEAL_PARENT_VERSION.pom" || return 1
python3 .travis/strip-bundlor.py /tmp/seal-parent.pom "$SEAL_PARENT_POM" || return 1

java -version
mvn -version
