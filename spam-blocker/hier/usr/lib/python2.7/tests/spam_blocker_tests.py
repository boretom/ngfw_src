import unittest2
import time
import sys
import os
import subprocess
from jsonrpc import ServiceProxy
from jsonrpc import JSONRPCException
from uvm import Manager
from uvm import Uvm
import remote_control
from tests.spam_blocker_base_tests import SpamBlockerBaseTests
import test_registry

#
# Just extends the spam base tests
#
class SpamBlockerTests(SpamBlockerBaseTests):

    @staticmethod
    def appName():
        return "spam-blocker"

    @staticmethod
    def shortName():
        return "spam-blocker"

    @staticmethod
    def displayName():
        return "Spam Blocker"

    # verify daemon is running
    def test_009_IsRunning(self):
        result = subprocess.call("ps aux | grep spamd | grep -v grep >/dev/null 2>&1", shell=True)
        assert (result == 0)
        result = subprocess.call("ps aux | grep spamcatd | grep -v grep >/dev/null 2>&1", shell=True)
        assert ( result == 0 )

    # verify MAIL_SHELL is scoring. Relies on test_20_smtpTest
    def test_021_check_for_mailshell(self):
        events = global_functions.get_events(self.displayName(),'Quarantined Events',None,1)
        if ( events != None ):
            assert( events.get('list') != None )
            assert('MAILSHELL_SCORE_' in events['list'][0]['spam_blocker_tests_string'])
        else:
            raise unittest2.SkipTest('No events to check for MAIL_SHELL')            

test_registry.registerApp("spam-blocker", SpamBlockerTests)
