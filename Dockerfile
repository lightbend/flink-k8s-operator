#
# Scala and java are added to centos. Modeled after fabric8/java-centos-openjdk8-jdk:1.5.1 and added scala
#

FROM centos:7.6.1810

USER root

RUN mkdir -p /deployments

# JAVA_APP_DIR is used by run-java.sh for finding the binaries
ENV JAVA_APP_DIR=/deployments \
    JAVA_MAJOR_VERSION=8

# Scala
ENV SCALA_VERSION 2.12.8

# /dev/urandom is used as random source, which is prefectly safe
# according to http://www.2uo.de/myths-about-urandom/
RUN yum install -y \
       java-1.8.0-openjdk-1.8.0.191.b12-1.el7_6 \
       java-1.8.0-openjdk-devel-1.8.0.191.b12-1.el7_6 \
    && echo "securerandom.source=file:/dev/urandom" >> /usr/lib/jvm/java/jre/lib/security/java.security \
    && yum clean all

ENV JAVA_HOME /etc/alternatives/jre

# Agent bond including Jolokia and jmx_exporter
ADD docker/agent-bond-opts /opt/run-java-options
RUN mkdir -p /opt/agent-bond \
 && curl http://central.maven.org/maven2/io/fabric8/agent-bond-agent/1.2.0/agent-bond-agent-1.2.0.jar \
          -o /opt/agent-bond/agent-bond.jar \
 && chmod 444 /opt/agent-bond/agent-bond.jar \
 && chmod 755 /opt/run-java-options
ADD docker/jmx_exporter_config.yml /opt/agent-bond/
EXPOSE 8778 9779

##
# Install Scala
## Piping curl directly in tar

RUN   mkdir -p /root && \
  curl -fsL https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo "export PATH=~/scala-$SCALA_VERSION/bin:$PATH" >> /root/.bashrc

ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=2 -XshowSettings:vm"

# Run under user "jboss" and prepare for be running
# under OpenShift, too
RUN groupadd -r jboss -g 1000 \
  && useradd -u 1000 -r -g jboss -m -d /opt/jboss -s /sbin/nologin jboss \
  && chmod 755 /opt/jboss \
  && chown -R jboss /deployments \
  && usermod -g root -G `id -g jboss` jboss \
  && chmod -R "g+rwX" /deployments \
  && chown -R jboss:root /deployments

# Create directory for operator jar
RUN  mkdir -p /operator && \
     chmod -R 777 /operator && \
     chown -R jboss:root /operator

USER jboss
