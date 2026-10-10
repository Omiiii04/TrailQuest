import xml.etree.ElementTree as ET

tree = ET.parse('window_dump.xml')
for node in tree.iter():
    t = node.attrib.get('text', '')
    d = node.attrib.get('content-desc', '')
    b = node.attrib.get('bounds', '')
    c = node.attrib.get('class', '')
    if t or d:
        print(f"[{c}] text='{t}' desc='{d}' bounds={b}")
