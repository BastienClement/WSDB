import subprocess
from pathlib import Path
from subprocess import *

imgs = Path("images")
for file in imgs.iterdir():
	if not file.name.endswith(".gif"):
		continue
	proc = Popen(["identify",  "-format",  "%wx%h",  str(file)], stdout=PIPE)
	(out, err) = proc.communicate()
	proc.wait()
	(width, height) = out.decode("utf8").split("x")
	width = int(width)
	height = int(height)
	if width > height:
		print(width, height, file)
		call(["convert", str(file), "-rotate", "90", str(file)])
