from __future__ import annotations

from datetime import UTC, date, datetime
from unittest import TestCase

import pytest

from custom_components.fitorb.relay import (
    MAX_RELAY_ID_LENGTH,
    MAX_RELAY_RAW_HEX_LENGTH,
    MAX_RELAY_SHORT_STRING_LENGTH,
    MAX_RELAY_VALUE_STRING_LENGTH,
    RelayAckResult,
    RelayMetric,
    RelayRejectedSample,
    parse_relay_batch,
    relay_ack_to_json,
)


def _payload() -> dict[str, object]:
    return {
        "relay_id": "pixel-8",
        "ring_id": "AA:BB:CC:DD:EE:FF",
        "app_version": "0.1.0",
        "protocol_version": 1,
        "sent_at": "2026-07-03T10:00:00Z",
        "backlog": 0,
        "samples": [
            {
                "sample_id": "sample-heart-1",
                "ring_id": "AA:BB:CC:DD:EE:FF",
                "metric": "heart_rate",
                "timestamp": "2026-07-03T09:55:00Z",
                "value": 72,
                "unit": "bpm",
                "source": "android_relay",
                "captured_at": "2026-07-03T09:55:05Z",
                "local_date": "2026-07-03",
                "protocol_version": 1,
            }
        ],
    }


def test_parse_relay_batch_normalizes_timestamps_and_metric() -> None:
    batch = parse_relay_batch(_payload(), max_samples=10)

    assert batch.relay_id == "pixel-8"
    assert batch.ring_id == "AA:BB:CC:DD:EE:FF"
    assert batch.sent_at == datetime(2026, 7, 3, 10, 0, tzinfo=UTC)
    assert batch.backlog == 0
    assert len(batch.samples) == 1
    assert batch.samples[0].metric is RelayMetric.HEART_RATE
    assert batch.samples[0].timestamp == datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
    assert batch.samples[0].local_date == date(2026, 7, 3)


def test_parse_relay_batch_rejects_oversized_batches() -> None:
    payload = _payload()
    payload["samples"] = payload["samples"] * 2

    with pytest.raises(ValueError, match="too many samples"):
        parse_relay_batch(payload, max_samples=1)


class TestRelayParserSafety(TestCase):
    def test_rejects_non_finite_float_values(self) -> None:
        for sample_value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(sample_value=sample_value):
                payload = _payload()
                sample = dict(payload["samples"][0])
                sample["value"] = sample_value
                payload["samples"] = [sample]

                with self.assertRaisesRegex(ValueError, "finite"):
                    parse_relay_batch(payload, max_samples=10)

    def test_rejects_oversized_batch_strings(self) -> None:
        cases = (
            ("relay_id", "r" * (MAX_RELAY_ID_LENGTH + 1)),
            ("ring_id", "r" * (MAX_RELAY_ID_LENGTH + 1)),
            ("app_version", "a" * (MAX_RELAY_SHORT_STRING_LENGTH + 1)),
        )

        for field, value in cases:
            with self.subTest(field=field):
                payload = _payload()
                payload[field] = value

                with self.assertRaisesRegex(ValueError, f"{field} is too long"):
                    parse_relay_batch(payload, max_samples=10)

    def test_rejects_oversized_sample_strings(self) -> None:
        cases = (
            ("sample_id", "s" * (MAX_RELAY_ID_LENGTH + 1)),
            ("ring_id", "r" * (MAX_RELAY_ID_LENGTH + 1)),
            ("metric", "m" * (MAX_RELAY_SHORT_STRING_LENGTH + 1)),
            ("unit", "u" * (MAX_RELAY_SHORT_STRING_LENGTH + 1)),
            ("source", "s" * (MAX_RELAY_SHORT_STRING_LENGTH + 1)),
            ("raw_hex", "A" * (MAX_RELAY_RAW_HEX_LENGTH + 1)),
        )

        for field, value in cases:
            with self.subTest(field=field):
                payload = _payload()
                sample = dict(payload["samples"][0])
                sample[field] = value
                payload["samples"] = [sample]

                with self.assertRaisesRegex(ValueError, f"{field} is too long"):
                    parse_relay_batch(payload, max_samples=10)

    def test_rejects_oversized_sample_string_value(self) -> None:
        payload = _payload()
        sample = dict(payload["samples"][0])
        sample["value"] = "v" * (MAX_RELAY_VALUE_STRING_LENGTH + 1)
        payload["samples"] = [sample]

        with self.assertRaisesRegex(ValueError, "value is too long"):
            parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_metric_mismatch() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["metric"] = "unknown_metric"
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="invalid metric"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_ring_id_mismatch() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["ring_id"] = "11:22:33:44:55:66"
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="ring_id mismatch"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_bool_batch_protocol_version() -> None:
    payload = _payload()
    payload["protocol_version"] = True

    with pytest.raises(ValueError, match="protocol_version"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_zero_batch_protocol_version() -> None:
    payload = _payload()
    payload["protocol_version"] = 0

    with pytest.raises(ValueError, match="protocol_version"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_bool_sample_protocol_version() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["protocol_version"] = False
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="protocol_version"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_zero_sample_protocol_version() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["protocol_version"] = 0
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="protocol_version"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_allows_missing_backlog() -> None:
    payload = _payload()
    payload.pop("backlog")

    batch = parse_relay_batch(payload, max_samples=10)

    assert batch.backlog is None


@pytest.mark.parametrize("backlog", [-1, True])
def test_parse_relay_batch_rejects_invalid_backlog(backlog: object) -> None:
    payload = _payload()
    payload["backlog"] = backlog

    with pytest.raises(ValueError, match="backlog"):
        parse_relay_batch(payload, max_samples=10)


def test_relay_ack_to_json_uses_zulu_server_time() -> None:
    response = relay_ack_to_json(
        RelayAckResult(
            accepted=("sample-heart-1",),
            duplicates=("sample-heart-0",),
            rejected=(RelayRejectedSample("bad-sample", "invalid_metric"),),
            server_time=datetime(2026, 7, 3, 10, 2, tzinfo=UTC),
        )
    )

    assert response == {
        "accepted": ["sample-heart-1"],
        "duplicates": ["sample-heart-0"],
        "rejected": [{"sample_id": "bad-sample", "reason": "invalid_metric"}],
        "server_time": "2026-07-03T10:02:00Z",
    }
