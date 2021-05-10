
ARG JENKINS_VER

# Note that Jenkins is based on Debian 9
FROM jenkins/jenkins:"${JENKINS_VER}"

ENV JENKINS_ADMIN_USER=$JENKINS_ADMIN_USER \
    JENKINS_ADMIN_PASSWORD=$JENKINS_ADMIN_USER

# If we want to install via apt we need to be root.
USER root

# We want to use bash and we want to set pipefail
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Install needed dependencies and build tools
RUN apt-get update -qq \
    && apt-get install --no-install-recommends -qq -y gradle maven ant apt-transport-https gnupg ruby

# Add dotnet core
RUN curl https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor > microsoft.asc.gpg \
    && mv microsoft.asc.gpg /etc/apt/trusted.gpg.d/ \
    && curl -O https://packages.microsoft.com/config/debian/9/prod.list \
    && mv prod.list /etc/apt/sources.list.d/microsoft-prod.list \
    && chown root:root /etc/apt/trusted.gpg.d/microsoft.asc.gpg \
    && chown root:root /etc/apt/sources.list.d/microsoft-prod.list

RUN apt-get update -qq \
    && apt-get install --no-install-recommends -qq -y dotnet-sdk-3.1

# Nodejs, nuget
RUN curl -sL https://deb.nodesource.com/setup_10.x | bash - \
    && apt-get install --no-install-recommends -qq -y nodejs nuget

# Clean up
RUN apt-get clean && rm -rf /var/lib/apt/list

# Install yarn and some nuget packages for yarn and npm audit
RUN npm install -g npm-audit-html yarn yarn-audit-html audit-ci

# Create a tools folder for manual installs
RUN mkdir -p /home/jenkins/tools

# Install snyk and dependencies
RUN curl --location https://github.com/snyk/snyk/releases/download/v1.563.0/snyk-linux --output /home/jenkins/tools/snyk-linux \
    && chmod 750 /home/jenkins/tools/snyk-linux \
    && ln -s /home/jenkins/tools/snyk-linux /usr/local/bin

RUN curl --location https://github.com/snyk/snyk-to-html/releases/download/v1.14.1/snyk-to-html-linux --output /home/jenkins/tools/snyk-to-html-linux \
    && chmod 750 /home/jenkins/tools/snyk-to-html-linux \
    && ln -s /home/jenkins/tools/snyk-to-html-linux /usr/local/bin

# Install OWASP Dependency Check under Jenkins where the db can be written
RUN gem install bundle-audit && gem cleanup
RUN curl --location https://github.com/jeremylong/DependencyCheck/releases/download/v6.1.5/dependency-check-6.1.5-release.zip --output /home/jenkins/tools/dependency-check-6.1.5-release.zip \
    && unzip /home/jenkins/tools/dependency-check-6.1.5-release.zip -d /home/jenkins/tools/ \
    && rm /home/jenkins/tools/dependency-check-6.1.5-release.zip \
    && ln -s /home/jenkins/tools/dependency-check/bin/dependency-check.sh /usr/local/bin

# Make jenkins user on all manually installed tools
RUN chown -R jenkins:jenkins /home/jenkins/tools

# Skip setup wizard for Jenkins
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Bootstrap the Jenkins plugins
COPY config/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt

# Bootstrap with Jenkins Configuration as Code (casC) plugin
COPY config/jenkins.yml /var/jenkins_home/jenkins.yml
RUN chown jenkins:jenkins /var/jenkins_home/jenkins.yml \
    && chmod 750 /var/jenkins_home/jenkins.yml

# Drop back down to Jenkins user
USER jenkins