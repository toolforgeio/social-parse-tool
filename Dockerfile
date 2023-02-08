FROM docker.toolforge.io/public/ubuntu:22.04

RUN apt-get update \
    && apt-get -y -q --no-install-recommends install openjdk-17-jre-headless

WORKDIR /root

COPY target/social-parse-tool.jar /root/
COPY manifest.yml /toolforge/manifest.yml

ENTRYPOINT [ "/usr/bin/java", "-jar", "social-parse-tool.jar" ]
