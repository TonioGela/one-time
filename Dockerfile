ARG sbt_version=1.5.6

FROM ghcr.io/graalvm/graalvm-ce:java11-21.3.0 as unzip
ARG sbt_version

RUN mkdir -pv "/usr/local/sbt" &&\
curl -L -o /tmp/sbt.tgz "https://github.com/sbt/sbt/releases/download/v$sbt_version/sbt-$sbt_version.tgz" &&\
tar xzf /tmp/sbt.tgz -C "/usr/local/sbt" --strip-components=1


FROM ghcr.io/graalvm/graalvm-ce:java11-21.3.0 as builder
COPY --from=unzip /usr/local/sbt /usr/local/sbt
RUN ln -sv "/usr/local/sbt/bin/sbt" /usr/bin/ && gu install native-image
ENV NATIVE_IMAGE_INSTALLED true
ENV GRAALVM_INSTALLED true
RUN mkdir /one-time
WORKDIR /one-time
RUN sbt sbtVersion
COPY . /one-time
RUN sbt nativeImage

FROM alpine:3.15.0
WORKDIR /
COPY --from=builder /one-time/target/native-image/one-time one-time

RUN adduser -D one-time-user
USER one-time-user

CMD ./one-time --baseUrl "${BASE_URL}" --timeout "${TIMEOUT}" --port "${PORT}"
