extends Control

var joystick_origin := Vector2.ZERO
var joystick_current := Vector2.ZERO
var joystick_active := false
var essence := 0
var morph := 0
var player_hp := 100
var enemy_hp := 5
var enemy_dead := false
var message := "Explore the cave. Collect mana crystals."
var message_until := 0

func _ready():
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_process(true)

func set_joystick(origin: Vector2, current: Vector2, active: bool):
	joystick_origin = origin
	joystick_current = current
	joystick_active = active
	queue_redraw()

func flash(text: String, ms := 1800):
	message = text
	message_until = Time.get_ticks_msec() + ms
	queue_redraw()

func _process(_delta):
	queue_redraw()

func _draw():
	var size = get_viewport_rect().size
	# Compact glass HUD panel.
	draw_style_box(make_panel(Color(0.012,0.025,0.045,0.8)), Rect2(20,18,520,144))
	draw_string(ThemeDB.fallback_font, Vector2(38,52), "SOUL SLIME — MOBILE TEST", HORIZONTAL_ALIGNMENT_LEFT, -1, 28, Color.WHITE)
	draw_string(ThemeDB.fallback_font, Vector2(38,86), "HP %d/100" % player_hp, HORIZONTAL_ALIGNMENT_LEFT, -1, 21, Color(0.65,1.0,0.85))
	draw_health_bar(Rect2(140,68,225,20), player_hp/100.0, Color(0.15,0.85,0.52))
	draw_string(ThemeDB.fallback_font, Vector2(38,119), "ESSENCE %d" % essence, HORIZONTAL_ALIGNMENT_LEFT, -1, 19, Color(0.38,0.88,1.0))
	draw_string(ThemeDB.fallback_font, Vector2(230,119), "MORPH %d%%" % morph, HORIZONTAL_ALIGNMENT_LEFT, -1, 19, Color(0.72,0.48,1.0))
	var enemy_text = "ECHO BAT — DOWN / ABSORB READY" if enemy_dead else "ECHO BAT  %d/5" % enemy_hp
	draw_string(ThemeDB.fallback_font, Vector2(38,148), enemy_text, HORIZONTAL_ALIGNMENT_LEFT, -1, 17, Color(1.0,0.6,0.55))

	# Analysis banner.
	var msg = message
	if message_until > 0 and Time.get_ticks_msec() > message_until:
		msg = "Left thumb moves • drag right side to look • attack / dodge / absorb"
	var banner_w = min(size.x - 56.0, 780.0)
	var banner_x = (size.x - banner_w) * 0.5
	draw_style_box(make_panel(Color(0.006,0.014,0.03,0.76)), Rect2(banner_x,24,banner_w,62))
	draw_string(ThemeDB.fallback_font, Vector2(banner_x+18,64), msg, HORIZONTAL_ALIGNMENT_CENTER, banner_w-36, 21, Color(0.92,0.98,1.0))

	# Soft center reticle only when exploring/combat; it makes the third-person camera feel intentional.
	draw_circle(Vector2(size.x*0.5,size.y*0.5), 10, Color(0.35,0.9,1.0,0.26), false, 2)
	draw_line(Vector2(size.x*0.5-18,size.y*0.5),Vector2(size.x*0.5-10,size.y*0.5),Color(0.4,0.9,1.0,0.42),2)
	draw_line(Vector2(size.x*0.5+10,size.y*0.5),Vector2(size.x*0.5+18,size.y*0.5),Color(0.4,0.9,1.0,0.42),2)

	# joystick
	var o = joystick_origin if joystick_active else Vector2(145,size.y-145)
	var c = joystick_current if joystick_active else o
	draw_circle(o,100,Color(0.04,0.12,0.2,0.42))
	draw_circle(o,100,Color(0.25,0.82,1.0,0.62),false,4)
	draw_circle(c,43,Color(0.25,0.9,1.0,0.84))
	draw_circle(c,26,Color(0.65,0.96,1.0,0.45),false,3)

func make_panel(color: Color) -> StyleBoxFlat:
	var p = StyleBoxFlat.new()
	p.bg_color = color
	p.corner_radius_top_left = 20
	p.corner_radius_top_right = 20
	p.corner_radius_bottom_left = 20
	p.corner_radius_bottom_right = 20
	p.border_width_left = 1
	p.border_width_top = 1
	p.border_width_right = 1
	p.border_width_bottom = 1
	p.border_color = Color(0.2,0.65,0.9,0.32)
	return p

func draw_health_bar(rect: Rect2, ratio: float, color: Color):
	draw_rect(rect,Color(0.03,0.06,0.09,0.8),true)
	draw_rect(Rect2(rect.position,Vector2(rect.size.x*clamp(ratio,0.0,1.0),rect.size.y)),color,true)
	draw_rect(rect,Color(0.55,0.85,1.0,0.35),false,1.5)
