# The NiFi extensions of the platform and the JDBC drivers its Pipelines load, packaged so a
# deployment can install them without a NiFi image of its own. The image is not run: an init
# container of the NiFi pod copies the NARs and the drivers out of it into volumes, and NiFi reads
# the NAR volume as a custom NAR library directory. See components/nifi in the
# civitas-core-deployment repository.
#
# The NARs and the drivers are built before the image, and the build context holds nothing else
# (.dockerignore):
#   mvn -f nifi-extensions/pom.xml package -DskipTests

FROM alpine:3.23.5

# The directory is created before the copy, because a path COPY --chmod creates inherits that mode
# and a directory without the execute bit cannot be entered.
RUN mkdir -p /opt/civitas/nars /opt/civitas/drivers

# World-readable: the pod that copies them runs as the operator's user, not as a user of this image.
COPY --chmod=444 nifi-*-nar/target/*.nar /opt/civitas/nars/
COPY --chmod=444 nifi-jdbc-drivers/target/drivers/*.jar /opt/civitas/drivers/

USER 1000:1000
