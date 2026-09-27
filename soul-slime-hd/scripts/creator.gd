extends Control

var preview_view: SubViewport
var slime
var preview_rect: TextureRect
var status_label: Label
var opacity_slider: HSlider
var aura_slider: HSlider
var rng := RandomNumberGenerator.new()
var bg_time := 0.0

var cfg := {
	"body_style": 0,
	"palette": 0,
	"eye_style": 0,
	"marking_style": 0,
	"core_style": 0,
	"opacity": 0.78,
	"aura": 0.55
}

const BODY_NAMES = ["ROUND", "DROP", "WIDE", "CRESTED"]
const PALETTE_NAMES = ["AZURE", "AMETHYST", "EMBER", "JADE", "PEARL", "SHADOW"]
const EYE_NAMES = ["CALM", "SHARP", "VOID", "STAR"]
const MARK_NAMES = ["NONE", "RUNE", "SPECKLES", "CREST"]
const CORE_NAMES = ["SOUL", "STAR", "MOON", "NONE"]
const PALETTE_COLORS = [
	Color(0.12,0.68,1.0),
	Color(0.52,0.26,0.96),
	Color(0.98,0.32,0.18),
	Color(0.20,0.78,0.48),
	Color(0.84,0.90,1.0),
	Color(0.10,0.08,0.16)
]

func _ready():
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_FULLSCREEN)
	DisplayServer.screen_set_orientation(DisplayServer.SCREEN_SENSOR_LANDSCAPE)
	rng.randomize()
	mouse_filter = Control.MOUSE_FILTER_PASS
	build_preview()
	load_saved()
	build_ui()
	apply_config()
	set_process(true)

func _process(delta):
	bg_time += delta
	queue_redraw()

func _draw():
	var size = get_viewport_rect().size
	draw_rect(Rect2(Vector2.ZERO,size),Color(0.004,0.009,0.02,1.0))
	# Layered faux-depth cave/mana backdrop.
	draw_circle(Vector2(size.x*0.20,size.y*0.42),size.y*0.48,Color(0.02,0.12,0.22,0.32))
	draw_circle(Vector2(size.x*0.38,size.y*0.58),size.y*0.36,Color(0.18,0.04,0.30,0.15))
	for i in 20:
		var px = fmod(float(i*137)+bg_time*(4.0+(i%4)), size.x+80.0)-40.0
		var py = fmod(float(i*83), size.y)
		var rr = 2.0 + float(i%4)
		draw_circle(Vector2(px,py),rr,Color(0.22,0.68,1.0,0.14+0.03*(i%3)))

func build_preview():
	preview_view = SubViewport.new()
	preview_view.size = Vector2i(2048,2048)
	preview_view.transparent_bg = true
	preview_view.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(preview_view)
	var slime_script = load("res://scripts/slime_canvas.gd")
	slime = Node2D.new()
	slime.set_script(slime_script)
	preview_view.add_child(slime)

func build_ui():
	var safe = MarginContainer.new()
	safe.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	safe.add_theme_constant_override("margin_left",28)
	safe.add_theme_constant_override("margin_right",28)
	safe.add_theme_constant_override("margin_top",22)
	safe.add_theme_constant_override("margin_bottom",24)
	add_child(safe)

	var root = HBoxContainer.new()
	root.add_theme_constant_override("separation",24)
	safe.add_child(root)

	var left = VBoxContainer.new()
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.48
	left.add_theme_constant_override("separation",10)
	root.add_child(left)

	var title = Label.new()
	title.text = "SOUL SLIME  •  CREATION"
	title.add_theme_font_size_override("font_size",31)
	title.add_theme_color_override("font_color",Color(0.88,0.97,1.0))
	left.add_child(title)

	var subtitle = Label.new()
	subtitle.text = "LIVE 2K CHARACTER RENDER  •  2048 × 2048"
	subtitle.add_theme_font_size_override("font_size",16)
	subtitle.add_theme_color_override("font_color",Color(0.35,0.76,1.0))
	left.add_child(subtitle)

	var preview_panel = PanelContainer.new()
	preview_panel.size_flags_vertical = Control.SIZE_EXPAND_FILL
	preview_panel.add_theme_stylebox_override("panel",panel_style(Color(0.015,0.035,0.065,0.82),Color(0.16,0.65,0.95,0.42),24))
	left.add_child(preview_panel)

	var preview_margin = MarginContainer.new()
	preview_margin.add_theme_constant_override("margin_left",12)
	preview_margin.add_theme_constant_override("margin_right",12)
	preview_margin.add_theme_constant_override("margin_top",12)
	preview_margin.add_theme_constant_override("margin_bottom",12)
	preview_panel.add_child(preview_margin)

	preview_rect = TextureRect.new()
	preview_rect.texture = preview_view.get_texture()
	preview_rect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview_rect.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	preview_rect.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	preview_rect.size_flags_vertical = Control.SIZE_EXPAND_FILL
	preview_margin.add_child(preview_rect)

	var bottom = HBoxContainer.new()
	bottom.add_theme_constant_override("separation",12)
	left.add_child(bottom)
	var random_btn = action_button("RANDOMIZE",Color(0.10,0.32,0.52,0.94))
	random_btn.pressed.connect(randomize_slime)
	bottom.add_child(random_btn)
	var save_btn = action_button("SAVE LOOK",Color(0.16,0.48,0.36,0.94))
	save_btn.pressed.connect(save_look)
	bottom.add_child(save_btn)
	var continue_btn = action_button("USE THIS SLIME",Color(0.43,0.16,0.66,0.96))
	continue_btn.pressed.connect(use_slime)
	continue_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bottom.add_child(continue_btn)

	var right_panel = PanelContainer.new()
	right_panel.custom_minimum_size = Vector2(430,0)
	right_panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	right_panel.size_flags_stretch_ratio = 0.95
	right_panel.add_theme_stylebox_override("panel",panel_style(Color(0.012,0.025,0.05,0.93),Color(0.28,0.45,0.78,0.46),22))
	root.add_child(right_panel)

	var right_margin = MarginContainer.new()
	right_margin.add_theme_constant_override("margin_left",18)
	right_margin.add_theme_constant_override("margin_right",18)
	right_margin.add_theme_constant_override("margin_top",18)
	right_margin.add_theme_constant_override("margin_bottom",18)
	right_panel.add_child(right_margin)

	var scroll = ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	right_margin.add_child(scroll)

	var options = VBoxContainer.new()
	options.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	options.add_theme_constant_override("separation",13)
	scroll.add_child(options)

	var head = Label.new()
	head.text = "BUILD YOUR FIRST FORM"
	head.add_theme_font_size_override("font_size",23)
	head.add_theme_color_override("font_color",Color.WHITE)
	options.add_child(head)

	status_label = Label.new()
	status_label.text = "Choose the form your soul awakens in."
	status_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	status_label.add_theme_font_size_override("font_size",15)
	status_label.add_theme_color_override("font_color",Color(0.62,0.76,0.88))
	options.add_child(status_label)

	add_choice_group(options,"BODY",BODY_NAMES,"body_style")
	add_palette_group(options)
	add_choice_group(options,"EYES",EYE_NAMES,"eye_style")
	add_choice_group(options,"MARKINGS",MARK_NAMES,"marking_style")
	add_choice_group(options,"SOUL CORE",CORE_NAMES,"core_style")

	add_section_label(options,"BODY TRANSPARENCY")
	opacity_slider = HSlider.new()
	opacity_slider.min_value = 0.48
	opacity_slider.max_value = 0.96
	opacity_slider.step = 0.01
	opacity_slider.value = float(cfg.opacity)
	opacity_slider.custom_minimum_size.y = 44
	opacity_slider.value_changed.connect(func(v): cfg.opacity=v; apply_config())
	options.add_child(opacity_slider)

	add_section_label(options,"AURA INTENSITY")
	aura_slider = HSlider.new()
	aura_slider.min_value = 0.0
	aura_slider.max_value = 1.0
	aura_slider.step = 0.01
	aura_slider.value = float(cfg.aura)
	aura_slider.custom_minimum_size.y = 44
	aura_slider.value_changed.connect(func(v): cfg.aura=v; apply_config())
	options.add_child(aura_slider)

	var note = Label.new()
	note.text = "This is your base slime. Later skills can add horns, elemental effects, wings, armor-like traits and evolved silhouettes."
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	note.add_theme_font_size_override("font_size",14)
	note.add_theme_color_override("font_color",Color(0.50,0.66,0.80))
	options.add_child(note)

func add_section_label(parent: VBoxContainer, text: String):
	var l = Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size",16)
	l.add_theme_color_override("font_color",Color(0.42,0.82,1.0))
	parent.add_child(l)

func add_choice_group(parent: VBoxContainer, label: String, names: Array, key: String):
	add_section_label(parent,label)
	var grid = GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation",8)
	grid.add_theme_constant_override("v_separation",8)
	parent.add_child(grid)
	var group = ButtonGroup.new()
	for i in names.size():
		var b = choice_button(names[i])
		b.toggle_mode = true
		b.button_group = group
		b.button_pressed = int(cfg[key]) == i
		b.pressed.connect(func(): cfg[key]=i; apply_config())
		grid.add_child(b)

func add_palette_group(parent: VBoxContainer):
	add_section_label(parent,"BODY COLOR")
	var grid = GridContainer.new()
	grid.columns = 3
	grid.add_theme_constant_override("h_separation",8)
	grid.add_theme_constant_override("v_separation",8)
	parent.add_child(grid)
	var group = ButtonGroup.new()
	for i in PALETTE_NAMES.size():
		var b = choice_button(PALETTE_NAMES[i])
		b.toggle_mode = true
		b.button_group = group
		b.button_pressed = int(cfg.palette) == i
		var st = button_style(PALETTE_COLORS[i].darkened(0.42),PALETTE_COLORS[i].lightened(0.18),14)
		b.add_theme_stylebox_override("normal",st)
		var pst = st.duplicate()
		pst.bg_color = PALETTE_COLORS[i].darkened(0.12)
		b.add_theme_stylebox_override("pressed",pst)
		b.add_theme_stylebox_override("hover",pst)
		b.pressed.connect(func(): cfg.palette=i; apply_config())
		grid.add_child(b)

func choice_button(text: String) -> Button:
	var b = Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(0,54)
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b.add_theme_font_size_override("font_size",15)
	var normal = button_style(Color(0.04,0.08,0.13,0.94),Color(0.18,0.38,0.55,0.7),13)
	b.add_theme_stylebox_override("normal",normal)
	var pressed = button_style(Color(0.10,0.31,0.48,0.98),Color(0.32,0.82,1.0,0.95),13)
	b.add_theme_stylebox_override("pressed",pressed)
	b.add_theme_stylebox_override("hover",pressed)
	return b

func action_button(text: String, color: Color) -> Button:
	var b = Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(142,64)
	b.add_theme_font_size_override("font_size",17)
	var st = button_style(color,Color(color.lightened(0.28).r,color.lightened(0.28).g,color.lightened(0.28).b,0.92),18)
	b.add_theme_stylebox_override("normal",st)
	var p = st.duplicate()
	p.bg_color = color.lightened(0.16)
	b.add_theme_stylebox_override("pressed",p)
	b.add_theme_stylebox_override("hover",p)
	return b

func panel_style(bg: Color, border: Color, radius: int) -> StyleBoxFlat:
	var s = StyleBoxFlat.new()
	s.bg_color = bg
	s.border_color = border
	s.border_width_left = 1
	s.border_width_top = 1
	s.border_width_right = 1
	s.border_width_bottom = 1
	s.corner_radius_top_left = radius
	s.corner_radius_top_right = radius
	s.corner_radius_bottom_left = radius
	s.corner_radius_bottom_right = radius
	return s

func button_style(bg: Color, border: Color, radius: int) -> StyleBoxFlat:
	var s = panel_style(bg,border,radius)
	s.border_width_left = 2
	s.border_width_top = 2
	s.border_width_right = 2
	s.border_width_bottom = 2
	s.content_margin_left = 12
	s.content_margin_right = 12
	s.content_margin_top = 10
	s.content_margin_bottom = 10
	return s

func apply_config():
	if slime:
		slime.set_config(cfg)

func randomize_slime():
	cfg.body_style = rng.randi_range(0,BODY_NAMES.size()-1)
	cfg.palette = rng.randi_range(0,PALETTE_NAMES.size()-1)
	cfg.eye_style = rng.randi_range(0,EYE_NAMES.size()-1)
	cfg.marking_style = rng.randi_range(0,MARK_NAMES.size()-1)
	cfg.core_style = rng.randi_range(0,CORE_NAMES.size()-1)
	cfg.opacity = rng.randf_range(0.58,0.92)
	cfg.aura = rng.randf_range(0.25,0.95)
	if opacity_slider: opacity_slider.value = cfg.opacity
	if aura_slider: aura_slider.value = cfg.aura
	apply_config()
	status_label.text = "Random soul form generated. Tap any option to refine it."

func save_look():
	write_config()
	await get_tree().process_frame
	var image = preview_view.get_texture().get_image()
	var err = image.save_png("user://soul_slime_hd_preview.png")
	status_label.text = "Saved creator profile + 2048×2048 preview." if err == OK else "Profile saved. Preview capture failed."

func use_slime():
	write_config()
	status_label.text = "SLIME FORM LOCKED • Next build: reincarnation + cave awakening."

func write_config():
	var f = FileAccess.open("user://slime_creator.json",FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(cfg))

func load_saved():
	if not FileAccess.file_exists("user://slime_creator.json"):
		return
	var f = FileAccess.open("user://slime_creator.json",FileAccess.READ)
	if not f:
		return
	var parsed = JSON.parse_string(f.get_as_text())
	if parsed is Dictionary:
		for k in cfg.keys():
			if parsed.has(k):
				cfg[k] = parsed[k]
