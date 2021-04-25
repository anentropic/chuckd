FROM ghcr.io/graalvm/graalvm-ce:ol8-java11-21.1.0 AS base

RUN gu install native-image

FROM base AS gradle
# ------------
# copied from:
# https://github.com/keeganwitt/docker-gradle/blob/master/hotspot/jdk11/Dockerfile
# ------------
ENV GRADLE_HOME /opt/gradle

RUN set -o errexit -o nounset \
    && echo "Adding gradle user and group" \
    && groupadd --system --gid 1000 gradle \
    && useradd --system --gid gradle --uid 1000 --shell /bin/bash --create-home gradle \
    && mkdir /home/gradle/.gradle \
    && chown --recursive gradle:gradle /home/gradle \
    \
    && echo "Symlinking root Gradle cache to gradle Gradle cache" \
    && ln -s /home/gradle/.gradle /root/.gradle

# gradle cache
VOLUME /home/gradle/.gradle

WORKDIR /home/gradle

# I guess if we ever need to install a dependency via bzr SCM from gradle
# we have to find a source for bzr package, for now it seems non-essential
RUN microdnf update
RUN microdnf install \
        fontconfig \
        unzip \
        wget \
        \
#        bzr \
        git \
        git-lfs \
        mercurial \
        openssh-clients \
        subversion \
    && microdnf clean all

ENV GRADLE_VERSION 7.0
ARG GRADLE_DOWNLOAD_SHA256=eb8b89184261025b0430f5b2233701ff1377f96da1ef5e278af6ae8bac5cc305
RUN set -o errexit -o nounset \
    && echo "Downloading Gradle" \
    && wget --no-verbose --output-document=gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    \
    && echo "Checking download hash" \
    && echo "${GRADLE_DOWNLOAD_SHA256} *gradle.zip" | sha256sum --check - \
    \
    && echo "Installing Gradle" \
    && unzip gradle.zip \
    && rm gradle.zip \
    && mv "gradle-${GRADLE_VERSION}" "${GRADLE_HOME}/" \
    && ln --symbolic "${GRADLE_HOME}/bin/gradle" /usr/bin/gradle \
    \
    && echo "Testing Gradle installation" \
    && gradle --version

FROM gradle AS chuckd-builder

RUN mkdir /home/chuckd
WORKDIR /home/chuckd
COPY ./app ./app
COPY ./settings.gradle ./settings.gradle

RUN gradle --no-daemon -Pstatic=true nativeImage

FROM scratch AS chuckd

VOLUME /schemas
WORKDIR /schemas

COPY --from=chuckd-builder /home/chuckd/app/build/bin/chuckd /usr/local/bin/chuckd

ENTRYPOINT ["chuckd"]