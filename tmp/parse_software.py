import urllib.request
import re
import json

url = "https://raw.githubusercontent.com/MichaIng/DietPi/master/dietpi/dietpi-software"
req = urllib.request.urlopen(url)
content = req.read().decode('utf-8')

items = []
curr = None

for line in content.splitlines():
    l = line.strip()
    if l.startswith("software_id="):
        val = l.split("=", 1)[1].strip()
        if val.isdigit():
            if curr and curr.get("name"):
                items.append(curr)
            curr = {
                "id": int(val),
                "name": "",
                "desc": "",
                "docs": "",
                "catx": -1,
                "deps": ""
            }
        else:
            if curr and curr.get("name"):
                items.append(curr)
            curr = None
    elif curr is not None:
        if l.startswith("aSOFTWARE_NAME[$software_id]="):
            curr["name"] = l.split("=", 1)[1].strip("'\"")
        elif l.startswith("aSOFTWARE_DESC[$software_id]="):
            curr["desc"] = l.split("=", 1)[1].strip("'\"")
        elif l.startswith("aSOFTWARE_DOCS[$software_id]="):
            curr["docs"] = l.split("=", 1)[1].strip("'\"")
        elif l.startswith("aSOFTWARE_CATX[$software_id]="):
            c = l.split("=", 1)[1].strip("'\"")
            if c.isdigit():
                curr["catx"] = int(c)
        elif l.startswith("aSOFTWARE_DEPS[$software_id]="):
            curr["deps"] = l.split("=", 1)[1].strip("'\"")

if curr and curr.get("name"):
    items.append(curr)

print(f"Total parsed software titles: {len(items)}")
with open("dietpi_software_parsed.json", "w") as f:
    json.dump(items, f, indent=2)

print("Saved to dietpi_software_parsed.json")
