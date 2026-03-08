"""Quick smoke test for the Reticulum Python environment."""

def smoke_test():
    """Verify the Python environment and key imports work."""
    results = []

    # Test 1: Python version
    import sys
    results.append(f"Python {sys.version}")

    # Test 2: RNS import
    try:
        import RNS
        results.append(f"RNS {RNS.__version__} OK")
    except Exception as e:
        results.append(f"RNS FAIL: {e}")

    # Test 3: cryptography import
    try:
        import cryptography
        results.append(f"cryptography {cryptography.__version__} OK")
    except Exception as e:
        results.append(f"cryptography FAIL: {e}")

    # Test 4: rnsh import
    try:
        import rnsh
        ver = getattr(rnsh, "__version__", "unknown")
        results.append(f"rnsh {ver} OK")
    except Exception as e:
        results.append(f"rnsh FAIL: {e}")

    # Test 5: umsgpack (bundled with RNS)
    try:
        from RNS.vendor import umsgpack
        results.append("umsgpack (RNS.vendor) OK")
    except Exception as e:
        results.append(f"umsgpack FAIL: {e}")

    # Test 6: rnsh protocol classes
    try:
        from rnsh.protocol import (
            register_message_types,
            VersionInfoMessage,
            WindowSizeMessage,
            ExecuteCommandMesssage,
            StreamDataMessage,
        )
        results.append("rnsh.protocol OK")
    except Exception as e:
        results.append(f"rnsh.protocol FAIL: {e}")

    # Test 7: haven_reticulum module
    try:
        import haven_reticulum
        results.append("haven_reticulum OK")
    except Exception as e:
        results.append(f"haven_reticulum FAIL: {e}")

    return "\n".join(results)
