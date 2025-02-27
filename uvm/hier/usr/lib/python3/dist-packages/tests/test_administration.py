import pytest
import unittest

from tests.common import NGFWTestCase
import tests.global_functions as global_functions
import runtests.test_registry as test_registry
import runtests.remote_control as remote_control

@pytest.mark.administration_tests
class AdministrationTests(NGFWTestCase):
    not_an_app = True
    
    @staticmethod
    def module_name():
        return "administration-tests"
    
    def test_010_client_is_online(self):
        result = remote_control.is_online()
        assert (result == 0)
        
    # Tests Certificate information in Config > Administration > Certificates
    def test_020_certificate_authority_info(self):
        cert_manager = global_functions.uvmContext.certificateManager()
        root_cert_info = cert_manager.getRootCertificateInformation()
        
        # Checks for CN=[Arista Site], O=Arista, and L=Santa Clara
        cert_subjects = root_cert_info["certSubject"].split(", ")
        cn_found, o_found, l_found = False, False, False
        skip_str = "Untangle certs allowed on old versions. Skipping test."
        for subject in cert_subjects:
            if "CN=" in subject:
                cn_found = True
                assert(subject in ["CN=www.untangle.com","CN=edge.arista.com"])
            if "O=" in subject:
                o_found = True
                assert(subject in ["O=Untangle","O=Arista","O=Security"])
            if "L=" in subject:
                l_found = True
                assert(subject in ["L=Sunnyvale","L=Santa Clara"])
        assert(cn_found and o_found and l_found)
    
test_registry.register_module("administration-tests", AdministrationTests)
