extends Node2D

var body_style := 0
var palette := 0
var eye_style := 0
var marking_style := 0
var core_style := 0
var opacity := 0.78
var aura := 0.55
var t := 0.0

var palettes := [
	Color(0.12,0.68,1.0),
	Color(0.52,0.26,0.96),
	Color(0.98,0.32,0.18),
	Color(0.20,0.78,0.48),
	Color(0.84,0.90,1.0),
	Color(0.10,0.08,0.16)
]

func _process(delta):
	t += delta
	queue_redraw()

func set_config(cfg: Dictionary):
	body_style = int(cfg.get("body_style",0))
	palette = int(cfg.get("palette",0))
	eye_style = int(cfg.get("eye_style",0))
	marking_style = int(cfg.get("marking_style",0))
	core_style = int(cfg.get("core_style",0))
	opacity = float(cfg.get("opacity",0.78))
	aura = float(cfg.get("aura",0.55))
	queue_redraw()

func _draw():
	var c = palettes[clamp(palette,0,palettes.size()-1)]
	var squish = 1.0 + sin(t*2.2)*0.025
	var pts = body_points(body_style, squish)

	# aura layers
	for i in range(5,0,-1):
		var glow = pts.duplicate()
		var scale_amt = 1.0 + (i * 0.018 * aura)
		for j in glow.size():
			glow[j] = Vector2(1024,1030) + (glow[j]-Vector2(1024,1030))*scale_amt
		draw_colored_polygon(glow, Color(c.r,c.g,c.b,0.018*i*aura))

	# body shadow + body
	var shadow = []
	for p in pts:
		shadow.append(p + Vector2(0,34))
	draw_colored_polygon(shadow, Color(0.0,0.0,0.0,0.16))
	draw_colored_polygon(pts, Color(c.r,c.g,c.b,opacity))

	# subtle inner depth
	var inner = []
	for p in pts:
		inner.append(Vector2(1024,1030) + (p-Vector2(1024,1030))*0.965 + Vector2(0,24))
	draw_colored_polygon(inner, Color(c.darkened(0.28).r,c.darkened(0.28).g,c.darkened(0.28).b,0.10))

	# glossy highlights
	draw_ellipse(Vector2(760,650), 260, 150, Color(1,1,1,0.18*opacity), -0.35)
	draw_ellipse(Vector2(690,760), 105, 58, Color(1,1,1,0.10*opacity), -0.35)

	draw_markings(c)
	draw_core(c)
	draw_eyes()
	draw_mouth()

func body_points(style: int, squish: float) -> PackedVector2Array:
	var pts := PackedVector2Array()
	var center := Vector2(1024,1030)
	var rx := 610.0
	var ry := 500.0 * squish
	if style == 1:
		rx = 520
		ry = 585*squish
	elif style == 2:
		rx = 710
		ry = 430*squish
	elif style == 3:
		rx = 625
		ry = 505*squish
	for i in 128:
		var a = TAU * float(i) / 128.0
		var x = cos(a)
		var y = sin(a)
		var modx = 1.0
		var mody = 1.0
		if style == 1:
			modx = 0.74 + 0.26 * ((y+1.0)*0.5)
		elif style == 2:
			mody = 0.92 + 0.08*cos(a*2.0)
		elif style == 3:
			if y < -0.30:
				modx = 0.88 + 0.12*cos(a*3.0)
		var px = center.x + x*rx*modx
		var py = center.y + y*ry*mody
		if y > 0.55:
			py -= (y-0.55)*165.0
		pts.append(Vector2(px,py))
	return pts

func draw_ellipse(center: Vector2, rx: float, ry: float, color: Color, rot := 0.0):
	var pts := PackedVector2Array()
	for i in 64:
		var a = TAU*float(i)/64.0
		var p = Vector2(cos(a)*rx,sin(a)*ry).rotated(rot)+center
		pts.append(p)
	draw_colored_polygon(pts,color)

func draw_core(body_color: Color):
	if core_style == 3:
		return
	var center = Vector2(1024,1075)
	var pulse = 1.0 + sin(t*3.0)*0.05
	for i in range(4,0,-1):
		draw_circle(center, 150.0*pulse + i*25.0*aura, Color(0.35,0.92,1.0,0.018*i*aura))
	if core_style == 0:
		draw_circle(center, 118*pulse, Color(0.38,0.95,1.0,0.62))
		draw_circle(center-Vector2(22,28), 42*pulse, Color(1,1,1,0.52))
	elif core_style == 1:
		draw_colored_polygon(star_points(center,125*pulse,58*pulse,5),Color(0.92,0.82,0.30,0.74))
	elif core_style == 2:
		draw_circle(center, 118*pulse, Color(0.86,0.90,1.0,0.72))
		draw_circle(center+Vector2(48,-12), 102*pulse, Color(body_color.r,body_color.g,body_color.b,0.86))

func star_points(center: Vector2, outer: float, inner: float, count: int) -> PackedVector2Array:
	var pts := PackedVector2Array()
	for i in count*2:
		var r = outer if i%2==0 else inner
		var a = -PI/2 + PI*float(i)/count
		pts.append(center + Vector2(cos(a),sin(a))*r)
	return pts

func draw_eyes():
	var left = Vector2(835,905)
	var right = Vector2(1213,905)
	match eye_style:
		0:
			draw_ellipse(left,90,125,Color(0.03,0.05,0.08,0.95))
			draw_ellipse(right,90,125,Color(0.03,0.05,0.08,0.95))
			draw_circle(left-Vector2(22,32),28,Color(0.65,0.95,1,0.88))
			draw_circle(right-Vector2(22,32),28,Color(0.65,0.95,1,0.88))
		1:
			draw_colored_polygon(PackedVector2Array([left+Vector2(-105,-40),left+Vector2(98,-68),left+Vector2(74,82),left+Vector2(-76,92)]),Color(0.02,0.03,0.06,0.96))
			draw_colored_polygon(PackedVector2Array([right+Vector2(-98,-68),right+Vector2(105,-40),right+Vector2(76,92),right+Vector2(-74,82)]),Color(0.02,0.03,0.06,0.96))
		2:
			draw_circle(left,105,Color(0.01,0.01,0.02,0.98))
			draw_circle(right,105,Color(0.01,0.01,0.02,0.98))
			draw_circle(left,40,Color(0.62,0.22,1.0,0.95))
			draw_circle(right,40,Color(0.62,0.22,1.0,0.95))
		3:
			draw_colored_polygon(star_points(left,105,44,5),Color(1.0,0.92,0.48,0.95))
			draw_colored_polygon(star_points(right,105,44,5),Color(1.0,0.92,0.48,0.95))

func draw_mouth():
	var pts := PackedVector2Array()
	for i in 28:
		var a = PI*float(i)/27.0
		pts.append(Vector2(1024-125*cos(a),1110+48*sin(a)))
	draw_polyline(pts,Color(0.04,0.04,0.08,0.75),18.0,true)

func draw_markings(body_color: Color):
	if marking_style == 0:
		return
	var mc = Color(0.85,0.96,1.0,0.34)
	if marking_style == 1:
		draw_arc(Vector2(1024,810),180,-2.5,-0.65,32,mc,18,true)
		draw_line(Vector2(1024,655),Vector2(1024,745),mc,18,true)
		draw_circle(Vector2(1024,625),22,mc)
	elif marking_style == 2:
		for i in 15:
			var a = float(i)*2.399
			var r = 120.0 + 28.0*i
			var p = Vector2(1024,960)+Vector2(cos(a),sin(a))*min(r,430.0)
			draw_circle(p,16+(i%3)*5,Color(1,1,1,0.18))
	elif marking_style == 3:
		draw_arc(Vector2(1024,1040),420,PI*1.08,PI*1.92,64,mc,28,true)
		draw_arc(Vector2(1024,1040),350,PI*1.12,PI*1.88,64,Color(body_color.darkened(0.4).r,body_color.darkened(0.4).g,body_color.darkened(0.4).b,0.24),18,true)
