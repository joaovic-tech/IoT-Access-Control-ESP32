"""Gera o diagrama da montagem na protoboard.

Uso:
    python3 assets/src/circuito-protoboard.py | rsvg-convert -b white -o assets/circuito-protoboard.png
"""

P = 26  # distância entre furos (px)


def Y(r):
    return 230 + (r - 1) * P


# Ordem das colunas (esquerda -> direita): + − a b c d e | f g h i j + −
X = {'L+': 700, 'L-': 726, 'a': 780, 'b': 806, 'c': 832, 'd': 858, 'e': 884,
     'f': 962, 'g': 988, 'h': 1014, 'i': 1040, 'j': 1066, 'R+': 1120, 'R-': 1146}
RED, BLK, PUR, GRN, BLU = '#dc2626', '#111827', '#9333ea', '#16a34a', '#2563eb'
WHT = 'white'  # fio branco: desenhado com contorno cinza

o = []
a = o.append


def wire(pts, c, w=7):
    s = ' '.join(f'{x},{y}' for x, y in pts)
    a(f'<polyline points="{s}" fill="none" stroke="{c}" stroke-width="{w}" '
      f'stroke-linecap="round" stroke-linejoin="round" opacity="0.95"/>')


def white_wire(pts):
    wire(pts, '#6b7280', 9)
    wire(pts, '#ffffff', 5)


def end(x, y, c='#9ca3af'):
    a(f'<circle cx="{x}" cy="{y}" r="5" fill="{c}" stroke="#374151" stroke-width="1.5"/>')


a('<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080" '
  'font-family="DejaVu Sans, Arial, sans-serif">')
a('<rect width="1920" height="1080" fill="#fff"/>')
a('<text x="960" y="48" text-anchor="middle" font-size="32" font-weight="bold" fill="#1f2937">'
  'People Detect — Montagem na protoboard</text>')
a('<text x="960" y="82" text-anchor="middle" font-size="18" fill="#6b7280">'
  'ESP32-CAM + Sensor PIR + LED, alimentados por regulador de tensão (saída 5 V)</text>')

# ---- protoboard ----
a('<rect x="668" y="196" width="510" height="816" rx="14" fill="#f5f5f0" stroke="#c8c8bd" stroke-width="3"/>')
a('<rect x="905" y="200" width="36" height="808" fill="#e4e4dc"/>')
for k, c, off in (('L+', RED, -14), ('L-', BLU, 14), ('R+', RED, -14), ('R-', BLU, 14)):
    a(f'<line x1="{X[k]+off}" y1="215" x2="{X[k]+off}" y2="995" stroke="{c}" stroke-width="3"/>')
for k, c, s in (('L+', RED, '+'), ('L-', BLU, '−'), ('R+', RED, '+'), ('R-', BLU, '−')):
    a(f'<text x="{X[k]}" y="214" text-anchor="middle" font-size="16" font-weight="bold" fill="{c}">{s}</text>')
    a(f'<text x="{X[k]}" y="1006" text-anchor="middle" font-size="16" font-weight="bold" fill="{c}">{s}</text>')
for col in 'abcdefghij':
    a(f'<text x="{X[col]}" y="214" text-anchor="middle" font-size="14" fill="#555">{col}</text>')
    a(f'<text x="{X[col]}" y="1004" text-anchor="middle" font-size="14" fill="#555">{col}</text>')
for r in range(1, 31):
    a(f'<text x="753" y="{Y(r)+4}" text-anchor="middle" font-size="11" fill="#777">{r}</text>')
    a(f'<text x="1093" y="{Y(r)+4}" text-anchor="middle" font-size="11" fill="#777">{r}</text>')
    for k in X:
        a(f'<rect x="{X[k]-5}" y="{Y(r)-5}" width="10" height="10" rx="2" fill="#3f3f3a"/>')

# ---- fonte e regulador ----
a('<rect x="40" y="100" width="210" height="110" rx="12" fill="#f3f4f6" stroke="#374151" stroke-width="3"/>')
a('<text x="145" y="138" text-anchor="middle" font-size="19" font-weight="bold" fill="#1f2937">Fonte de entrada</text>')
a('<text x="145" y="162" text-anchor="middle" font-size="15" fill="#4b5563">(bateria / fonte DC)</text>')
a('<text x="236" y="146" text-anchor="end" font-size="16" font-weight="bold" fill="#dc2626">+</text>')
a('<text x="236" y="196" text-anchor="end" font-size="18" font-weight="bold" fill="#111">−</text>')
wire([(250, 140), (330, 140)], RED)
wire([(250, 190), (330, 190)], BLK)
a('<rect x="330" y="100" width="250" height="120" rx="12" fill="#dbeafe" stroke="#1d4ed8" stroke-width="3"/>')
a('<text x="455" y="155" text-anchor="middle" font-size="19" font-weight="bold" fill="#1e3a8a">Regulador de tensão</text>')
a('<text x="455" y="180" text-anchor="middle" font-size="15" fill="#1e40af">saída ajustada: 5 V</text>')
a('<text x="342" y="125" font-size="12" fill="#1e3a8a">IN+</text>'
  '<text x="342" y="212" font-size="12" fill="#1e3a8a">IN−</text>')
a('<text x="568" y="125" text-anchor="end" font-size="12" font-weight="bold" fill="#111">OUT−</text>'
  '<text x="568" y="212" text-anchor="end" font-size="12" font-weight="bold" fill="#7e22ce">OUT+</text>')
white_wire([(580, 140), (X['L-'], 140), (X['L-'], Y(1))])  # OUT− : fio branco
wire([(580, 190), (X['L+'], 190), (X['L+'], Y(1))], PUR)    # OUT+ : fio roxo
for x, y in ((330, 140), (330, 190), (580, 140), (580, 190)):
    end(x, y)
end(X['L+'], Y(1), PUR)
end(X['L-'], Y(1), '#fff')

# ---- sensor PIR (ordem física dos pinos: VCC, OUT, GND) ----
a('<text x="490" y="276" text-anchor="middle" font-size="19" font-weight="bold" fill="#1f2937">Sensor PIR HC-SR501</text>')
a('<rect x="380" y="290" width="220" height="160" rx="10" fill="#ecfdf5" stroke="#047857" stroke-width="3"/>')
a('<circle cx="455" cy="370" r="48" fill="#f8fafc" stroke="#9ca3af" stroke-width="3"/>')
a('<text x="455" y="375" text-anchor="middle" font-size="12" fill="#6b7280">lente</text>')
PV, PO, PG = 360, 399, 438  # altura dos pinos VCC, OUT, GND
for y, t, c in ((PV, 'VCC', RED), (PO, 'OUT', '#7e22ce'), (PG, 'GND', '#111')):
    a(f'<text x="588" y="{y+5}" text-anchor="end" font-size="14" font-weight="bold" fill="{c}">{t}</text>')
wire([(600, PV), (X['L+'], Y(6))], RED)                                        # VCC -> trilho + linha 6
wire([(600, PO), (766, PO), (766, Y(6)), (X['a'], Y(6))], PUR)                 # OUT -> a6 (GPIO 13)
wire([(600, PG), (620, PG), (620, Y(10)), (X['L-'], Y(10))], BLK)              # GND -> trilho − linha 10
for y in (PV, PO, PG):
    end(600, y, '#d4a017')
end(X['L+'], Y(6), RED)
end(X['a'], Y(6), PUR)
end(X['L-'], Y(10), '#111')

# ---- ESP32-CAM (pinos nas colunas b e i, linhas 3 a 10) ----
a('<rect x="790" y="246" width="266" height="358" rx="8" fill="#1e293b" stroke="#0f172a" stroke-width="3" opacity="0.97"/>')
a('<text x="923" y="268" text-anchor="middle" font-size="15" font-weight="bold" fill="#f1f5f9">ESP32-CAM</text>')
a('<rect x="885" y="500" width="76" height="76" rx="6" fill="#334155" stroke="#94a3b8" stroke-width="2"/>')
a('<circle cx="923" cy="538" r="24" fill="#0f172a" stroke="#64748b" stroke-width="3"/>'
  '<circle cx="923" cy="538" r="10" fill="#1e3a8a"/>')
L = ['5V', 'GND', 'IO12', 'IO13', 'IO15', 'IO14', 'IO2', 'IO4']
R = ['3V3', 'IO16', 'IO0', 'GND', 'VCC', 'U0R', 'U0T', 'GND']
hl = {0: '#fca5a5', 1: '#e5e7eb', 3: '#d8b4fe'}
for n, (l, rr) in enumerate(zip(L, R)):
    r = 3 + n
    cl = hl.get(n, '#94a3b8')
    cr = '#86efac' if rr == 'IO16' else '#94a3b8'
    a(f'<text x="{X["b"]+14}" y="{Y(r)+5}" font-size="13" font-weight="bold" fill="{cl}">{l}</text>')
    a(f'<text x="{X["i"]-14}" y="{Y(r)+5}" text-anchor="end" font-size="13" font-weight="bold" fill="{cr}">{rr}</text>')
    end(X['b'], Y(r), '#d4a017')
    end(X['i'], Y(r), '#d4a017')

# jumpers de alimentação da ESP32-CAM
wire([(X['a'], Y(3)), (X['L+'], Y(3))], RED)
end(X['a'], Y(3), RED)
end(X['L+'], Y(3), RED)
wire([(X['a'], Y(4)), (680, Y(4)), (680, Y(2)), (X['L-'], Y(2))], BLK)  # a4 -> trilho − linha 2
end(X['a'], Y(4), '#111')
end(X['L-'], Y(2), '#111')

# GPIO16 -> LED
wire([(X['j'], Y(4)), (X['j']+16, Y(4)+14), (X['j']+16, Y(18)-14), (X['j'], Y(18))], GRN)
end(X['j'], Y(4), GRN)
end(X['j'], Y(18), GRN)

# LED: ânodo h18, cátodo h19
x = X['h']
ym = (Y(18) + Y(19)) / 2
a(f'<line x1="{x}" y1="{Y(18)}" x2="{x-12}" y2="{ym-5}" stroke="#9ca3af" stroke-width="3"/>')
a(f'<line x1="{x}" y1="{Y(19)}" x2="{x-12}" y2="{ym+5}" stroke="#9ca3af" stroke-width="3"/>')
a(f'<circle cx="{x-26}" cy="{ym}" r="15" fill="#ef4444" stroke="#7f1d1d" stroke-width="2.5"/>')
a(f'<circle cx="{x-31}" cy="{ym-5}" r="4" fill="#fecaca" opacity="0.8"/>')
end(x, Y(18))
end(x, Y(19))

# Resistor 220 Ω: j19 -> trilho − direito (linha 21)
yr = Y(19)
wire([(X['j'], yr), (1108, yr), (X['R-'], Y(21))], '#9ca3af', 3)
a(f'<rect x="1070" y="{yr-8}" width="40" height="16" rx="7" fill="#e8c99b" stroke="#92400e" stroke-width="2"/>')
for bx, bc in ((1077, '#dc2626'), (1085, '#dc2626'), (1093, '#7c2d12'), (1102, '#ca8a04')):
    a(f'<rect x="{bx}" y="{yr-8}" width="4" height="16" fill="{bc}"/>')
end(X['j'], yr)
end(X['R-'], Y(21), BLU)

# Ponte GND: trilho − esquerdo -> trilho − direito (linha 30)
wire([(X['L-'], Y(30)), (X['L-'], 1040), (X['R-'], 1040), (X['R-'], Y(30))], BLK)
end(X['L-'], Y(30), '#111')
end(X['R-'], Y(30), '#111')
a('<text x="936" y="1064" text-anchor="middle" font-size="14" fill="#111">'
  'ponte GND: trilho − esquerdo ↔ trilho − direito</text>')

# rótulos laterais
a(f'<line x1="1180" y1="{ym}" x2="1220" y2="{ym}" stroke="#9ca3af" stroke-width="1.5" stroke-dasharray="4 3"/>')
a(f'<text x="1226" y="{ym-4}" font-size="15" font-weight="bold" fill="#1f2937">LED: ânodo (+, perna longa) em h18</text>')
a(f'<text x="1226" y="{ym+15}" font-size="13" fill="#4b5563">cátodo (−, perna curta) em h19</text>')
a(f'<line x1="1180" y1="{Y(21)+20}" x2="1220" y2="{Y(21)+20}" stroke="#9ca3af" stroke-width="1.5" stroke-dasharray="4 3"/>')
a(f'<text x="1226" y="{Y(21)+24}" font-size="15" font-weight="bold" fill="#1f2937">Resistor 220 Ω: j19 → trilho − direito, linha 21</text>')
a(f'<text x="1226" y="{Y(21)+43}" font-size="13" fill="#4b5563">(vermelho-vermelho-marrom)</text>')

# ---- tabela de ligações ----
a('<rect x="1250" y="110" width="630" height="440" rx="12" fill="#f9fafb" stroke="#d1d5db" stroke-width="2"/>')
a('<text x="1272" y="145" font-size="20" font-weight="bold" fill="#1f2937">Ligações (furo a furo)</text>')
rows = [(PUR, 'Regulador OUT+ (fio roxo) → trilho + esquerdo, linha 1'),
        (WHT, 'Regulador OUT− (fio branco) → trilho − esquerdo, linha 1'),
        ('#d4a017', 'ESP32-CAM: 5V em b3, GND em b4 (colunas b e i)'),
        (RED, 'a3 → trilho + esquerdo, linha 3 (5V da ESP)'),
        (BLK, 'a4 → trilho − esquerdo, linha 2 (GND da ESP)'),
        (RED, 'PIR VCC (fio vermelho) → trilho +, linha 6'),
        (PUR, 'PIR OUT (fio roxo) → a6 (GPIO 13)'),
        (BLK, 'PIR GND (fio preto) → trilho −, linha 10'),
        (GRN, 'j4 → j18 (GPIO 16 → ânodo do LED)'),
        ('#ef4444', 'LED: ânodo h18, cátodo h19'),
        ('#92400e', 'Resistor 220 Ω: j19 → trilho − direito, linha 21'),
        (BLK, 'Ponte: trilho − esq. (30) → trilho − dir. (30)')]
for n, (c, t) in enumerate(rows):
    y = 180 + n * 31
    if c == WHT:
        a(f'<line x1="1274" y1="{y-5}" x2="1310" y2="{y-5}" stroke="#6b7280" stroke-width="9" stroke-linecap="round"/>')
        c = '#ffffff'
    a(f'<line x1="1274" y1="{y-5}" x2="1310" y2="{y-5}" stroke="{c}" stroke-width="7" stroke-linecap="round"/>')
    a(f'<text x="1324" y="{y}" font-size="16" fill="#374151">{t}</text>')

# ---- como a protoboard conecta ----
a('<rect x="1250" y="810" width="630" height="190" rx="12" fill="#eff6ff" stroke="#93c5fd" stroke-width="2"/>')
a('<text x="1272" y="845" font-size="18" font-weight="bold" fill="#1e3a8a">Como a protoboard conecta</text>')
for n, t in enumerate(['• Furos a–e de uma mesma linha estão ligados entre si (idem f–j).',
                       '• O canal central separa o lado a–e do lado f–j.',
                       '• Cada trilho + / − é ligado na vertical (linhas 1 a 30).',
                       '• Trilhos da esquerda e da direita NÃO se ligam sozinhos:',
                       '   por isso a ponte de GND na linha 30.']):
    a(f'<text x="1272" y="{875+n*25}" font-size="15" fill="#1e3a8a">{t}</text>')

# ---- observações ----
a('<rect x="40" y="470" width="590" height="330" rx="12" fill="#fffbeb" stroke="#f59e0b" stroke-width="2"/>')
a('<text x="62" y="505" font-size="18" font-weight="bold" fill="#92400e">Observações</text>')
notes = ['• A ESP32-CAM é larga: com os pinos nas colunas b e i,',
         '  sobra só um furo livre por linha (a à esquerda, j à direita).',
         '  É por esses furos que entram os jumpers.',
         '• Meça a saída do regulador antes de ligar (~5,0 V).',
         '• Não alimente pelo regulador e pelo USB ao mesmo tempo.',
         '• Confira a ordem VCC / OUT / GND na serigrafia do',
         '  seu sensor PIR antes de ligar.',
         '• O trilho + da direita não é usado nesta montagem.',
         '• Fios que passam por cima de furos não se conectam',
         '  a eles: só a ponta (bolinha) entra no furo.']
for n, t in enumerate(notes):
    a(f'<text x="62" y="{537+n*25}" font-size="15" fill="#78350f">{t}</text>')

# ---- cores dos fios ----
a('<rect x="40" y="830" width="590" height="180" rx="12" fill="#f9fafb" stroke="#d1d5db" stroke-width="2"/>')
a('<text x="62" y="862" font-size="18" font-weight="bold" fill="#1f2937">Cores dos fios</text>')
for n, (c, t) in enumerate([(PUR, 'Regulador OUT+ (5 V)'), (WHT, 'Regulador OUT− (GND)'),
                            (RED, '+5 V / PIR VCC'), (BLK, 'GND (terra) / PIR GND'),
                            (PUR, 'PIR OUT → GPIO 13'), (GRN, 'GPIO 16 → LED')]):
    y = 890 + n * 21
    if c == WHT:
        a(f'<line x1="64" y1="{y-5}" x2="104" y2="{y-5}" stroke="#6b7280" stroke-width="9" stroke-linecap="round"/>')
        c = '#ffffff'
    a(f'<line x1="64" y1="{y-5}" x2="104" y2="{y-5}" stroke="{c}" stroke-width="7" stroke-linecap="round"/>')
    a(f'<text x="118" y="{y}" font-size="15" fill="#374151">{t}</text>')
a('<circle cx="380" cy="890" r="6" fill="#9ca3af" stroke="#374151" stroke-width="1.5"/>')
a('<text x="394" y="895" font-size="14" fill="#374151">= ponta do fio no furo</text>')
a('</svg>')

print('\n'.join(o))
