import urllib.request
import re
import json

base_doc_url = "https://raw.githubusercontent.com/MichaIng/DietPi-Docs/master/docs/software/"
image_raw_prefix = "https://raw.githubusercontent.com/MichaIng/DietPi-Docs/master/docs/assets/images/"

doc_files = [
    ('advanced_networking.md', 'Advanced Networking'),
    ('bittorrent.md', 'BitTorrent & Downloads'),
    ('camera.md', 'Camera & Surveillance'),
    ('cloud.md', 'Cloud & Backup'),
    ('databases.md', 'Databases & Data Stores'),
    ('desktop.md', 'Desktops'),
    ('distributed_projects.md', 'Distributed Projects'),
    ('dns_servers.md', 'DNS Servers & Ad Blocking'),
    ('file_servers.md', 'File Servers'),
    ('gaming.md', 'Gaming & Emulation'),
    ('hardware_projects.md', 'Hardware Projects & IoT'),
    ('home_automation.md', 'Home Automation'),
    ('log_system.md', 'Logging Systems'),
    ('media.md', 'Media & Audio Streaming'),
    ('printing.md', 'Printing Servers'),
    ('programming.md', 'Development & Programming'),
    ('remote_desktop.md', 'Remote Desktop & Access'),
    ('social.md', 'Social & Search'),
    ('ssh.md', 'SSH Servers'),
    ('system_security.md', 'System Security'),
    ('system_software.md', 'System Software'),
    ('system_stats.md', 'System Stats & Management'),
    ('vpn.md', 'VPN'),
    ('webserver_stack.md', 'Web Development & Stacks')
]

# Map of title lower to details
doc_sections = []

for filename, cat_name in doc_files:
    try:
        url = base_doc_url + filename
        resp = urllib.request.urlopen(url)
        text = resp.read().decode('utf-8')
        
        # Split text by ## 
        sections = re.split(r'\n##\s+', text)
        for sec in sections[1:]: # skip header
            lines = sec.splitlines()
            title_line = lines[0].strip()
            # Clean title e.g. "AdGuard Home" or "LAMP web stack"
            title = re.sub(r'\{#[^}]+\}', '', title_line).strip()
            
            # Find Official website
            m_web = re.search(r'Official website:\s*<([^>]+)>', sec)
            homepage = m_web.group(1).strip() if m_web else ""
            if not homepage:
                m_web2 = re.search(r'Official website:\s*\[([^\]]+)\]\(([^)]+)\)', sec)
                if m_web2:
                    homepage = m_web2.group(2).strip()
            if not homepage:
                m_gh = re.search(r'Source code:\s*<([^>]+)>', sec)
                if m_gh:
                    homepage = m_gh.group(1).strip()

            # Find image: ![...](../assets/images/...)
            m_img = re.search(r'!\[[^\]]*\]\(\.\./assets/images/([^)\s"]+)', sec)
            image_url = ""
            if m_img:
                img_name = m_img.group(1).strip()
                image_url = image_raw_prefix + img_name

            # Find description (first non-empty text line)
            desc = ""
            for l in lines[1:]:
                l_s = l.strip()
                if l_s and not l_s.startswith('![') and not l_s.startswith('===') and not l_s.startswith('```') and not l_s.startswith('!!!'):
                    desc = l_s
                    break
            
            doc_sections.append({
                "title": title,
                "category": cat_name,
                "homepage": homepage,
                "image_url": image_url,
                "desc": desc,
                "doc_page": f"https://dietpi.com/docs/software/{filename.replace('.md', '')}/#{title.lower().replace(' ', '-')}"
            })
    except Exception as e:
        print(f"Error fetching {filename}: {e}")

print(f"Parsed {len(doc_sections)} doc sections.")

# Now load parsed_software.json
with open("dietpi_software_parsed.json") as f:
    software_list = json.load(f)

# Correlate
enriched = []
for sw in software_list:
    sw_name = sw["name"].strip()
    # Try exact match or fuzzy match
    matched = None
    for ds in doc_sections:
        if sw_name.lower() == ds["title"].lower() or sw_name.lower() in ds["title"].lower() or ds["title"].lower() in sw_name.lower():
            matched = ds
            break
            
    homepage = matched["homepage"] if matched and matched["homepage"] else ""
    image_url = matched["image_url"] if matched and matched["image_url"] else ""
    category = matched["category"] if matched else "General Software"
    doc_link = sw["docs"] if sw["docs"] else (matched["doc_page"] if matched else "https://dietpi.com/docs/software/")

    enriched.append({
        "id": sw["id"],
        "name": sw_name,
        "desc": sw["desc"],
        "category": category,
        "homepage": homepage,
        "imageUrl": image_url,
        "docs": doc_link,
        "deps": sw.get("deps", "")
    })

print(f"Enriched {len(enriched)} software items.")
with open("app/src/main/assets/dietpi_software.json", "w") as f:
    json.dump(enriched, f, indent=2)

print("Saved to app/src/main/assets/dietpi_software.json")
