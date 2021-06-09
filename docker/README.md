# Aeneas Node in Docker

## About Aeneas
Aeneas is a decentralized platform that allows any user to issue, transfer, swap and trade custom blockchain tokens on an integrated peer-to-peer exchange. You can find more information about Aeneas at [aeneas.pm](https://aeneas.pm).


## About the image
This Docker image contains scripts and configs to run Aeneas Node for `mainnet`, 'testnet' or 'stagenet' networks.
The image is focused on fast and convenient deployment of Aeneas Node.

## Prerequisites
It is highly recommended to read more about node configuration before running the container.

## Building Docker image

Dockerfile supports 3 main scenarios:
1. Basic scenario `docker build .` - build an image with the latest Aeneas Node release available
*Note*: pre-releases are skipped
2. Existing Version scenario `docker build --build-arg AENEAS_VERSION=1.1.1` - specify the version of Aeneas Node available in GitHub Releases. If this version does not exist, this is the next scenario.
3. Build scenario `docker build --build-arg AENEAS_VERSION=99.99.99 --build-arg BRANCH=version-0.17.x` - this scenario assumes that you want to build Aeneas Node from sources. Use `AENEAS_VERSION` build argument to specify a Git tag ('v' is added automatically) and `BRANCH` to specify a Git branch to checkout to. Make sure you specify a tag that does not exist in the repo, otherwise it is the previous scenario.

**You can specify following aarguments when building the inage:**


|Argument              | Default value |Description   |
|----------------------|-------------------|--------------|
|`AENEAS_NETWORK`       | `mainnet`         | Aeneas Blockchain network. Available values are `mainnet`, `testnet`, `stagenet`. Can be overridden in a runtime using environment variable with the same name.|
|`AENEAS_VERSION`       | `latest`            | A node version which corresponds to the Git tag we want to use/create. |
|`BRANCH`              | `version-0.17.x`    | Relevant if Git tag 'v`AENEAS_VERSION`' does not exist in the public repository. This option represents a Git branch we will use to compile Aeneas node and set a Git tag on.|
|`SBT_VERSION`         | `1.2.8` 	       | Scala build tool version.|
|`AENEAS_LOG_LEVEL`     | `DEBUG`           | Default Aeneas Node log level. Available values: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. More details about logging are available here. Can be overridden in a runtime using environment variable with the same name. |
|`AENEAS_HEAP_SIZE`     | `2g`              | Default Aeneas Node JVM Heap Size limit in -X Command-line Options notation (`-Xms=[your value]`). More details [here](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionX.html). Can be overridden in a runtime using environment variable with the same name. |

**Note: All build arguments are optional.**  

## Running Docker image

### Configuration options

1. The image supports Aeneas Node config customization. To change a config field use corrresponding JVM options. JVM options can be sent to JVM using `JAVA_OPTS` environment variable.

2. Aeneas Node is looking for a config in the directory `/etc/aeneas/aeneas.conf` which can be mounted using Docker volumes. If this directory does not exist, a default configuration will be copied to this directory. Default configuration is chosen depending on `AENEAS_NETWORK` environment variable. If the value of `AENEAS_NETWORK` is not `mainnet`, `testnet` or `stagenet`, default configuration won't be applied. This is a scenario of using `CUSTOM` network - correct configuration must be provided. If you use `CUSTOM` network and `/etc/aeneas/aeneas.conf` is NOT found Aeneas Node container will exit.

3. By default, `/etc/aeneas/aeneas.conf` config includes `/etc/aeneas/local.conf`. Custom `/etc/aeneas/local.conf` can be used to override default config entries. Custom `/etc/aeneas/aeneas.conf` can be used to override or the whole configuration. For additional information about Docker volumes mapping please refer to `Managing data` item.

### Environment Variables

**You can run container with predefined environment variables:**

**Note: All variables are optional.**  

**Note: Environment variables override values in the configuration file.** 


### Managing data
We recommend to store the blockchain state as well as Aeneas configuration on the host side. As such, consider using Docker volumes mapping to map host directories inside the container:

**Example:**

1. Create a directory to store Aeneas data:

```
mkdir -p /docker/aeneas
mkdir /docker/aeneas/aeneas-data
mkdir /docker/aeneas/aeneas-config
```

Once container is launched it will create:

- three subdirectories in `/docker/aeneas/aeneas-data`:
```
/docker/aeneas/aeneas-data/log    - Aeneas Node logs
/docker/aeneas/aeneas-data/data   - Aeneas Blockchain state
/docker/aeneas/aeneas-data/wallet - Aeneas Wallet data
```
- `/docker/aeneas/aeneas-config/aeneas.conf` - default Aeneas config


3. If you already have Aeneas Node configuration/data - place it in the corresponsing directories


4. *Configure access permissions*. We use `aeneas` user with predefined uid/gid `143/143` to launch the container. As such, either change permissions of the created directories or change their owner:


### Blockchain state

If you are a Aeneas Blockchain newbie and launching Aeneas Node for the first time be aware that after launch it will start downloading the whole blockchain state from the other nodes. During this download it will be verifying all blocks one after another. This procesure can take some time.

You can speed this process up by downloading a compressed blockchain state from our official resources, extract it and mount inside the container (as discussed in the previous section). In this scenario Aeneas Node skips block verifying. This is a reason why it takes less time. This is also a reason why you must download blockchain state *only from our official resources*.

**Note**: We do not guarantee the state consistency if it's downloaded from third-parties.

**Example:**
```

```

### Network Ports

1. REST-API interaction with Node. 

2. Aeneas Node communication port for incoming connections. 

**Example:**
Below command will launch a container:
- with REST-API port enabled and configured on the socket `0.0.0.0:6870`
- Aeneas node communication port enabled and configured on the socket `0.0.0.0:6868`
- Ports `6868` and `6870` mapped from the host to the container

```
```

Check that REST API is up by navigating to the following URL from the host side:
http://localhost:6870/api-docs/index.html
