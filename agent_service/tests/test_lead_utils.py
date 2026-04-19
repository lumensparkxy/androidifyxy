from agent_service.tools.lead_tools import (
    _build_doc_id,
    _build_initial_routing_fields,
    _generate_request_number,
    _infer_lead_category,
    _normalize_product_name,
)
from agent_service.tools.profile_tools import (
    _missing_required_fields,
    build_lead_farmer_profile_snapshot,
)


def test_normalize_product_name_collapses_whitespace() -> None:
    assert _normalize_product_name("  Neem   Oil  ") == "neem oil"


def test_build_doc_id_is_stable() -> None:
    left = _build_doc_id("user-1", "conv-1", "Neem Oil")
    right = _build_doc_id("user-1", "conv-1", " neem   oil ")
    assert left == right
    assert len(left) == 32


def test_generate_request_number_prefix() -> None:
    request_number = _generate_request_number()
    assert request_number.startswith("KR-")
    assert len(request_number.split("-")) == 3


def test_infer_lead_category_detects_fertilizer() -> None:
    assert _infer_lead_category("Urea 50kg", "I want to buy urea for my crop") == "fertilizer"


def test_infer_lead_category_detects_pesticide() -> None:
    assert _infer_lead_category("Neem spray", "Need pesticide for sucking pest") == "pesticide"


def test_build_initial_routing_fields_populates_location_and_defaults() -> None:
    fields = _build_initial_routing_fields(
        {
            "district": "Pune",
            "tehsil": "Haveli",
            "village": "Wagholi",
        },
        "Hybrid seed pack",
        "Need 10 packets for sowing",
    )

    assert fields["leadCategory"] == "seed"
    assert fields["leadLocation"] == {
        "district": "Pune",
        "districtKey": "pune",
        "tehsil": "Haveli",
        "tehsilKey": "haveli",
        "village": "Wagholi",
        "villageKey": "wagholi",
    }
    assert fields["routingStatus"] == "initiated"
    assert fields["reviewStatus"] == "pending_recommendation"
    assert fields["recommendationStatus"] == "pending"
    assert fields["supplierVisibility"] == "hidden"
    assert fields["suggestedSupplier"] is None
    assert fields["selectedSupplier"] is None
    assert fields["assignedSupplier"] is None
    assert fields["commissionPreview"] == {
        "category": "seed",
        "amount": None,
        "currency": "INR",
        "ruleId": None,
    }


def test_build_lead_farmer_profile_snapshot_normalizes_contact_aliases() -> None:
    snapshot = build_lead_farmer_profile_snapshot(
        {
            "name": " Ramesh Patil ",
            "district": " Pune ",
            "village": " Baramati ",
            "tehsil": " Daund ",
            "totalFarmAcres": "5",
            "phoneNumber": "+91 98765 43210",
            "email": " ramesh@example.com ",
            "majorCrops": ["soybean"],
        }
    )

    assert snapshot == {
        "name": "Ramesh Patil",
        "district": "Pune",
        "village": "Baramati",
        "tehsil": "Daund",
        "totalFarmAcres": 5,
        "mobileNumber": "9876543210",
        "emailId": "ramesh@example.com",
        "email": "ramesh@example.com",
    }


def test_missing_required_fields_requires_mobile_number() -> None:
    missing = _missing_required_fields(
        {
            "name": "Ramesh Patil",
            "district": "Pune",
            "village": "Baramati",
            "tehsil": "Daund",
            "totalFarmAcres": 5,
        }
    )

    assert missing == ["mobileNumber"]


