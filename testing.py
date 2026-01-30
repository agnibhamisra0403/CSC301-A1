import json
import requests
import os

# Configuration based on your config.json
BASE_URL = "http://127.0.0.1:14006"
PAYLOADS_DIR = "CSC301_2025_A1/CSC301_A1_testcases/payloads"
RESPONSES_DIR = "CSC301_2025_A1/CSC301_A1_testcases/responses"

def run_test_suite(category):
    payload_file = os.path.join(PAYLOADS_DIR, f"{category}_testcases.json")
    response_file = os.path.join(RESPONSES_DIR, f"{category}_responses.json")
    
    with open(payload_file) as f:
        payloads = json.load(f)
    with open(response_file) as f:
        expected_responses = json.load(f)

    print(f"\n--- Running {category.upper()} Tests ---")
    
    for test_name, payload in payloads.items():
        expected = expected_responses.get(test_name)
        
        # Determine Endpoint and Method
        if "user" in category:
            endpoint = "/user"
            # Handle GET requests which are structured differently in payloads
            if "get" in test_name:
                url = f"{BASE_URL}{endpoint}/{payload['id']}"
                response = requests.get(url)
            else:
                response = requests.post(f"{BASE_URL}{endpoint}", json=payload)
        elif "product" in category:
            endpoint = "/product"
            if "get" in test_name:
                url = f"{BASE_URL}{endpoint}/{payload['id']}"
                response = requests.get(url)
            else:
                response = requests.post(f"{BASE_URL}{endpoint}", json=payload)
        else:
            endpoint = "/order"
            response = requests.post(f"{BASE_URL}{endpoint}", json=payload)

        # Validation
        actual_json = response.json() if response.text and response.text != "{}" else {}
        
        # In A1, IDs can be random, so we often ignore them in comparisons
        if "id" in actual_json and "id" not in expected:
            actual_json.pop("id")

        success = (actual_json == expected)
        status_marker = "PASS" if success else "FAIL"
        
        print(f"[{status_marker}] {test_name}")
        if not success:
            print(f"   Expected: {expected}")
            print(f"   Actual:   {actual_json}")
            print(f"   Status Code: {response.status_code}")

if __name__ == "__main__":
    # The instructions state tests run in Order: User -> Product -> Order
    for cat in ["user", "product", "order"]:
        run_test_suite(cat)