import pytest
from tools.network import start_network, wait_for_started_network, wait_for_converged_network
from tools.rnode import start_bootstrap


@pytest.fixture(scope="package")
def complete_network(config, docker, validators_data):
    with start_bootstrap(docker,
                         config.node_startup_timeout,
                         config.rnode_timeout,
                         validators_data) as bootstrap_node:

        with start_network(config,
                     docker,
                     bootstrap_node,
                     validators_data) as n:

            wait_for_started_network(config.node_startup_timeout, n)

            wait_for_converged_network(config.network_converge_timeout, n, len(n.peers))

            yield n

@pytest.fixture(scope="package")
def star_network(config, docker, validators_data):
    with start_bootstrap(docker,
                         config.node_startup_timeout,
                         config.rnode_timeout,
                         validators_data) as bootstrap_node:

        with start_network(config,
                     docker,
                     bootstrap_node,
                     validators_data,
                     [bootstrap_node.name]) as n:

            wait_for_started_network(config.node_startup_timeout, n)

            wait_for_converged_network(config.network_converge_timeout, n, 1)

            yield n