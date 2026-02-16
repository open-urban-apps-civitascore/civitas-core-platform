# Tests for decision log masking policy
#
# Verifies that sensitive authentication headers are masked in decision logs.

package system.log_test

import rego.v1

import data.system.log

# The mask set should contain paths for all sensitive headers

test_masks_access_token if {
	"/input/request/headers/X-Access-Token" in log.mask
}

test_masks_access_token_lowercase if {
	"/input/request/headers/x-access-token" in log.mask
}

test_masks_userinfo if {
	"/input/request/headers/X-Userinfo" in log.mask
}

test_masks_authorization_lowercase if {
	"/input/request/headers/authorization" in log.mask
}

test_masks_authorization_titlecase if {
	"/input/request/headers/Authorization" in log.mask
}

test_mask_count if {
	count(log.mask) == 5
}
