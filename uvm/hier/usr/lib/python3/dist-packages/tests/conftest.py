import datetime
import pytest

import runtests

# FIXME: these need to be transformed into command-line switches as well
runtests.remote_control.external_interface = 1
runtests.remote_control.interface = 2


def is_enabled(value):
    return value in ['true', 'True', '1', 't', 'y', 'yes']


def set_client_ip(value):
    runtests.remote_control.client_ip = value
    return runtests.remote_control.client_ip


def set_skip_instantiated(value):
    runtests.skip_instantiated = is_enabled(value)
    return runtests.skip_instantiated


def set_quick_tests_only(value):
    runtests.quick_tests_only = is_enabled(value)
    return runtests.quick_tests_only


def pytest_addoption(parser):
    parser.addoption("--runtests-host",
                     type=set_client_ip,
                     default="192.168.122.122",
                     help="client IP")

    parser.addoption("--skip-instantiated",
                     type=set_skip_instantiated,
                     default=True,
                     help="skip tests if app is already instantiated")

    parser.addoption("--quick-tests-only",
                     type=set_quick_tests_only,
                     default=False,
                     help="only run quick tests")


runtests.test_start_time = datetime.datetime.now()
