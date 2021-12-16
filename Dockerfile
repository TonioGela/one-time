
FROM ghcr.io/graalvm/graalvm-ce:java11-21.3.0 as builder

RUN gu install native-image 
RUN curl -fLo cs https://git.io/coursier-cli-linux && chmod +x cs && ./cs install sbt
ENV PATH /root/.local/share/coursier/bin:$PATH
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

CMD ./one-time --baseUri "${BASE_URI}" --timeout "${TIMEOUT}" --port "${PORT}"
