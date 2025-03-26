import json
import glob
import os

# Directory containing SARIF files
sarif_directory = "."
output_file = os.path.join(sarif_directory, "merged_results.sarif")

print(f"Looking for SARIF files in: {sarif_directory}")

# Initialize the merged SARIF structure
merged_sarif = None
merged_results = []

# Function to clean the 'uri' field inside 'artifactLocation' blocks from CI paths so that Github
# can interpret them correctly and create the annotation.
def clean_artifact_location_uris(results):
    for result in results:
        locations = result.get("locations", [])
        for location in locations:
            physical_location = location.get("physicalLocation", {})
            artifact_location = physical_location.get("artifactLocation", {})
            if "uri" in artifact_location and isinstance(artifact_location["uri"], str):
                # Remove 'work/android/android/' from the URI
                artifact_location["uri"] = artifact_location["uri"].replace("work/android/android/", "")

# Iterate over all SARIF files in the directory
for sarif_file in glob.glob(os.path.join(sarif_directory, "**/*.sarif"), recursive=True):
    print(f"Processing SARIF file: {sarif_file}")
    with open(sarif_file, "r") as file:
        sarif_data = json.load(file)
        if merged_sarif is None:
            # Use the first file's structure as the base
            merged_sarif = sarif_data
        # Merge the `results` array
        merged_results.extend(sarif_data.get("runs", [])[0].get("results", []))

# Clean up 'uri' fields in the 'artifactLocation' blocks
clean_artifact_location_uris(merged_results)

# Update the merged SARIF structure with the combined results
if merged_sarif:
    merged_sarif["runs"][0]["results"] = merged_results
    
    # Write the merged SARIF to a new file
    with open(output_file, "w") as file:
        json.dump(merged_sarif, file, indent=4)

    print(f"Merged SARIF file created at: {output_file}")
else:
    print("No SARIF files found or invalid SARIF structure.")
