import unittest
from types import SimpleNamespace
from unittest.mock import patch

from flask import Flask

from civitas_security import CivitasSupersetSecurityManager, SupersetSecurityManager


class FakeLoginManager:
    def request_loader(self, loader):
        return loader


class DeleteAccessTest(unittest.TestCase):
    def setUp(self):
        app = Flask(__name__)
        manager = CivitasSupersetSecurityManager.__new__(CivitasSupersetSecurityManager)
        manager._public_dashboard_filter_installed = True

        with patch.object(
            SupersetSecurityManager,
            "create_login_manager",
            return_value=FakeLoginManager(),
        ):
            manager.create_login_manager(app)

        @app.route("/api/v1/<resource>/", methods=["DELETE", "POST"])
        @app.route("/api/v1/<resource>/<item_id>", methods=["DELETE", "PUT"])
        def resource(resource, item_id=None):
            return "allowed"

        self.client = app.test_client()
        self.service_username = "service-account-for-test"
        self.service_environment = patch.dict(
            "os.environ", {"SUPERSET_SERVICE_USERNAME": self.service_username}
        )
        self.service_environment.start()
        self.addCleanup(self.service_environment.stop)

    def assert_access(self, username, authenticated, method, path, status):
        with patch(
            "civitas_security.current_user",
            SimpleNamespace(username=username, is_authenticated=authenticated),
        ):
            response = self.client.open(path, method=method)
        self.assertEqual(response.status_code, status)

    def test_user_cannot_delete_dashboard_or_dataset(self):
        for resource in ("dashboard", "dataset"):
            for path in (f"/api/v1/{resource}/", f"/api/v1/{resource}/123"):
                with self.subTest(path=path):
                    self.assert_access("human-admin", True, "DELETE", path, 403)

    def test_anonymous_user_cannot_delete(self):
        self.assert_access(None, False, "DELETE", "/api/v1/dashboard/123", 403)

    def test_service_account_can_delete_dashboard_and_dataset(self):
        for resource in ("dashboard", "dataset"):
            for path in (f"/api/v1/{resource}/", f"/api/v1/{resource}/123"):
                with self.subTest(path=path):
                    self.assert_access(self.service_username, True, "DELETE", path, 200)

    def test_user_can_still_create_and_edit(self):
        self.assert_access("human-admin", True, "POST", "/api/v1/dashboard/", 200)
        self.assert_access("human-admin", True, "PUT", "/api/v1/dataset/123", 200)

    def test_missing_service_identity_fails_closed(self):
        with patch.dict("os.environ", {"SUPERSET_SERVICE_USERNAME": ""}):
            self.assert_access(self.service_username, True, "DELETE", "/api/v1/dashboard/123", 403)
