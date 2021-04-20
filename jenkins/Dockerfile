# Note that Jenkins is based on Debian 9
FROM jenkins/jenkins:2.277.2-lts

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

# Install snyk, yarn and some nuget packages for yarn and npm audit
RUN npm install -g npm-audit-html snyk yarn yarn-audit-html audit-ci

# Install OWASP Dependency Check under Jenkins where the db can be written
RUN gem install bundle-audit && gem cleanup
RUN curl --location https://github.com/jeremylong/DependencyCheck/releases/download/v6.1.0/dependency-check-6.1.0-release.zip --output /opt/dependency-check-6.1.0-release.zip \
    && unzip /opt/dependency-check-6.1.0-release.zip -d /opt/ \
    && ln -s /opt/dependency-check/bin/dependency-check.sh /usr/bin/dependency-check \
    && rm /opt/dependency-check-6.1.0-release.zip

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