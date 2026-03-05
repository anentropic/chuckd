FROM ghcr.io/graalvm/native-image-community:21 AS base

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

RUN microdnf update
RUN microdnf install \
        fontconfig \
        unzip \
        wget \
        \
        git \
        git-lfs \
        mercurial \
        openssh-clients \
        subversion \
    && microdnf clean all

ENV GRADLE_VERSION 8.14.4
ARG GRADLE_DOWNLOAD_SHA256=f1771298a70f6db5a29daf62378c4e18a17fc33c9ba6b14362e0cdf40610380d
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

RUN gradle --no-daemon -Pstatic=true nativeCompile

FROM scratch AS chuckd

VOLUME /schemas
WORKDIR /schemas

COPY --from=chuckd-builder /home/chuckd/app/build/native/nativeCompile/chuckd /usr/local/bin/chuckd

ENTRYPOINT ["chuckd"]