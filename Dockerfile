FROM ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive

# Installs dependencies from source/README.md
RUN apt-get update && apt-get install -y openjdk-11-jdk openjdk-8-jdk maven gradle git python3 python3-pip

# Installs pre-built z3
RUN git clone https://github.com/Z3Prover/z3.git && \
    cd z3 && \
    git checkout 23c53c6820b2d0c786dc416dab9a50473a7bbde3 && \
    python3 scripts/mk_make.py --java && \
    cd build && \
    make && \
    make install

# Set Z3_HOME env variable
ENV Z3_HOME=/z3

# Copies repo into container
COPY . /Respector-morans-minions

WORKDIR /Respector-morans-minions/source

# Compiles Respector
RUN mvn package

WORKDIR /Respector-morans-minions
