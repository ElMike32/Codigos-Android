import os
import re
import io
import threading
import requests
import certifi
import urllib3
import pandas as pd
import qrcode

# Componentes de Kivy para UI Android
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.popup import Popup
from kivy.graphics.texture import Texture
from kivy.uix.image import Image
from kivy.utils import platform

# ReportLab para la exportación PDF
from reportlab.lib.pagesizes import letter, ELEVENSEVENTEEN
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Spacer, Paragraph, PageBreak
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.graphics.shapes import Drawing, Image as RLImage, String
from reportlab.lib.units import inch

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

URL_EXCEL = "https://docs.google.com/spreadsheets/d/1h6kjomnvvirjrSoMUvorNJa56RTi1X8E/export?format=xlsx"
URL_LOGO = "https://raw.githubusercontent.com/ElMike32/logo/main/logo.png"

# ==============================================================================
# UTILIDADES Y RUTAS EN ANDROID
# ==============================================================================
def obtener_carpeta_descargas_android():
    """ En Android guarda en la carpeta pública Download o almacenamiento de la App """
    if platform == 'android':
        from android.storage import primary_external_storage_path
        dir_base = primary_external_storage_path()
        descargas = os.path.join(dir_base, 'Download')
        if os.path.exists(descargas):
            return descargas
        return App.get_running_app().user_data_dir
    else:
        return os.path.abspath(".")

def limpiar_texto(val):
    if pd.isna(val): return ""
    s = str(val).strip()
    return "" if s.lower() in ['nan', 'none'] else s

def limpiar_entero(val):
    s = limpiar_texto(val)
    if not s: return ""
    try:
        return str(int(float(s)))
    except ValueError:
        return s

def normalizar_texto(texto):
    texto = limpiar_texto(texto)
    reemplazos = (("á", "a"), ("é", "e"), ("í", "i"), ("ó", "o"), ("ú", "u"))
    texto = texto.lower()
    for a, b in reemplazos:
        texto = texto.replace(a, b)
    return texto

def limpiar_nombre(nombre):
    return re.sub(r'[\\/*?:"<>|]', "", str(nombre)).strip()

def obtener_texto_cantidad_etiqueta(qty):
    q = limpiar_entero(qty)
    if q in ["", "0"]: return "1 PALLET"
    try:
        num = int(q)
        if num == 1: return "1 CAJA"
        elif num > 1: return f"{num} PIEZAS"
    except ValueError: pass
    return f"{q} PIEZAS"

def descargar_logo(url_logo, logo_filename="logo.png"):
    if not os.path.exists(logo_filename):
        try:
            try:
                res = requests.get(url_logo, timeout=10, verify=certifi.where())
            except Exception:
                res = requests.get(url_logo, timeout=10, verify=False)
            res.raise_for_status()
            with open(logo_filename, "wb") as f:
                f.write(res.content)
        except Exception as e:
            print(f"[AVISO] Logo no descargado: {e}")

def pil_to_kivy_texture(pil_image):
    pil_image = pil_image.convert("RGBA")
    data = pil_image.tobytes()
    texture = Texture.create(size=pil_image.size, colorfmt='rgba')
    texture.blit_buffer(data, colorfmt='rgba', bufferfmt='ubyte')
    texture.flip_vertical()
    return texture

# ==============================================================================
# MOTOR PDF (ReportLab)
# ==============================================================================
def generar_qr_dinamico_pdf(componente, qty, maquina, tiene_x=False):
    cantidad_str = "" if qty == "nan" or not qty or qty.isspace() else qty
    datos_qr = f"5X{componente}/{cantidad_str}/{maquina}/930" if tiene_x else f"/{componente}/{cantidad_str}/{maquina}"
    qr = qrcode.QRCode(version=1, box_size=5, border=1)
    qr.add_data(datos_qr)
    qr.make(fit=True)
    return qr.make_image(fill_color="black", back_color="white")

def generar_qr_empaque_pdf(material_empaque, qty, maquina):
    cantidad_str = "" if qty == "nan" or not qty or qty.isspace() else qty
    datos_qr = f"1X{material_empaque}/{cantidad_str}/{maquina}"
    qr = qrcode.QRCode(version=1, box_size=5, border=1)
    qr.add_data(datos_qr)
    qr.make(fit=True)
    return qr.make_image(fill_color="black", back_color="white")

def construir_pdf_por_tamano(df, df_pallet_base, df_single_base, seleccion_material, seleccion_maquina, logo_path, carpeta_salida, c_idx, cols_info, tamano_hoja="Carta"):
    col_material, col_maquina, col_componente, col_descripcion, col_definicion, col_cantidad, col_x, c_p_mat, c_p_desc, c_p_qty, c_p_ref_mat, c_s_mat, c_s_desc, c_s_qty, c_s_ref_mat = cols_info

    nombre_limpio_maquina = limpiar_nombre(seleccion_maquina)
    nombre_limpio_material = limpiar_nombre(seleccion_material)
    pdf_salida = os.path.join(carpeta_salida, f"{nombre_limpio_maquina}_{nombre_limpio_material}.pdf")

    is_doble_carta = (tamano_hoja == "Doble Carta")
    page_size = ELEVENSEVENTEEN if is_doble_carta else letter
    margin_side, margin_top, margin_bottom = (20, 20, 20) if is_doble_carta else (14, 14, 14)

    doc = SimpleDocTemplate(pdf_salida, pagesize=page_size, leftMargin=margin_side, rightMargin=margin_side, topMargin=margin_top, bottomMargin=margin_bottom)
    story = []

    df_filtrado = df[(df[col_material] == seleccion_material) & (df[col_maquina] == seleccion_maquina)]
    maquina_global = seleccion_maquina

    def_material_txt = ""
    if col_definicion and not df_filtrado.empty:
        def_material_txt = str(df_filtrado.iloc[0][col_definicion]).strip()
        if def_material_txt == 'nan': def_material_txt = ""

    df_pallet_filtrado = pd.DataFrame()
    if not df_pallet_base.empty and c_p_ref_mat:
        df_p = df_pallet_base.copy()
        df_p[c_p_ref_mat] = df_p[c_p_ref_mat].astype(str).str.strip()
        df_pallet_filtrado = df_p[df_p[c_p_ref_mat] == seleccion_material]

    df_single_filtrado = pd.DataFrame()
    if not df_single_base.empty and c_s_ref_mat:
        df_s = df_single_base.copy()
        df_s[c_s_ref_mat] = df_s[c_s_ref_mat].astype(str).str.strip()
        df_single_filtrado = df_s[df_s[c_s_ref_mat] == seleccion_material]

    styles = getSampleStyleSheet()
    estilo_desc = ParagraphStyle('DescStyle', parent=styles['Normal'], fontName='Helvetica', 
                                 fontSize=12 if is_doble_carta else 9, 
                                 leading=14 if is_doble_carta else 11, 
                                 textColor=colors.HexColor("#444444"))

    lista_celdas_etiquetas = []
    temp_dir = os.path.join(carpeta_salida, "temp_qrs")
    if not os.path.exists(temp_dir):
        os.makedirs(temp_dir)

    for idx, fila in df_filtrado.iterrows():
        componente = str(fila[col_componente]).strip()
        descripcion = str(fila[col_descripcion]).strip() if col_descripcion else ""
        qty = str(fila[col_cantidad]).strip() if col_cantidad else ""
        valor_x = str(fila[col_x]).strip().lower() if col_x else ""
        tiene_x = (valor_x == "x")

        texto_pie_qr = obtener_texto_cantidad_etiqueta(qty)
        if descripcion == 'nan': descripcion = ""
        if qty in ['nan', '0', '0.0']: qty = " "
        if qty.endswith('.0'): qty = qty[:-2]

        img_qr = generar_qr_dinamico_pdf(componente, qty, maquina_global, tiene_x=tiene_x)
        qr_filename = os.path.join(temp_dir, f"qr_h1_{c_idx}_{idx}.png")
        img_qr.save(qr_filename)

        if is_doble_carta:
            f_size_comp = 18 if len(componente) <= 12 else 15
            f_size_desc = 12 if len(descripcion) <= 22 else 10
            ancho_txt, row_h = 2.3 * inch, 0.45 * inch
            ancho_qr_cont, alto_qr_cont, tam_qr = 1.3 * inch, 0.9 * inch, 0.65 * inch
            pos_y_add, font_pie = 6, 9
            w_tarjeta, h_tarjeta, w_contenedor = 3.6 * inch, 1.0 * inch, 5.1 * inch
            pad_l_izq, pad_r_izq = 10, 6
            pad_l_der, pad_r_der = 6, 10
        else:
            f_size_comp = 13 if len(componente) <= 12 else 11
            f_size_desc = 9 if len(descripcion) <= 22 else 7.5
            ancho_txt, row_h = 1.6 * inch, 0.34 * inch
            ancho_qr_cont, alto_qr_cont, tam_qr = 0.9 * inch, 0.68 * inch, 0.4 * inch
            pos_y_add, font_pie = 4, 7
            w_tarjeta, h_tarjeta, w_contenedor = 2.5 * inch, 0.75 * inch, 4.05 * inch
            pad_l_izq, pad_r_izq = 8, 4
            pad_l_der, pad_r_der = 4, 8

        estilo_comp_dinamico = ParagraphStyle('CD', fontName='Helvetica-Bold', fontSize=f_size_comp, leading=f_size_comp+2)
        estilo_desc_dinamico = ParagraphStyle('DD', fontName='Helvetica', fontSize=f_size_desc, leading=f_size_desc+2, textColor=colors.HexColor("#444444"))

        p_comp = Paragraph(componente, estilo_comp_dinamico)
        p_desc = Paragraph(descripcion, estilo_desc_dinamico)

        tabla_sub_texto = Table([[p_comp], [p_desc]], colWidths=[ancho_txt], rowHeights=[row_h, row_h])
        tabla_sub_texto.setStyle(TableStyle([
            ('ALIGN', (0,0), (-1,-1), 'LEFT'), ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
            ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
            ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
        ]))

        pos_x = (ancho_qr_cont - tam_qr) / 2.0
        pos_y = ((alto_qr_cont - tam_qr) / 2.0) + pos_y_add

        d = Drawing(ancho_qr_cont, alto_qr_cont)
        d.add(RLImage(pos_x, pos_y, tam_qr, tam_qr, qr_filename))
        d.add(String(ancho_qr_cont / 2.0, 2, texto_pie_qr, fontName='Helvetica-Bold', fontSize=font_pie, textAnchor='middle'))

        lista_celdas_etiquetas.append((tabla_sub_texto, d, ancho_txt, ancho_qr_cont, h_tarjeta, w_contenedor, pad_l_izq, pad_r_izq, pad_l_der, pad_r_der))

    grid_datos = []
    for i in range(0, len(lista_celdas_etiquetas), 2):
        fila_grid = []
        txt_izq, qr_izq, w_t, w_q, h_t, w_c, pl_i, pr_i, pl_d, pr_d = lista_celdas_etiquetas[i]
        tarjeta_izq = Table([[txt_izq, qr_izq]], colWidths=[w_t, w_q], rowHeights=[h_t])
        tarjeta_izq.setStyle(TableStyle([
            ('VALIGN', (0,0), (-1,-1), 'MIDDLE'), ('ALIGN', (1,0), (1,0), 'CENTER'),
            ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#222222")),
            ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
            ('LEFTPADDING', (0,0), (-1,-1), pl_i), ('RIGHTPADDING', (0,0), (-1,-1), pr_i),
        ]))

        contenedor_izq = Table([[tarjeta_izq]], colWidths=[w_c])
        contenedor_izq.setStyle(TableStyle([
            ('ALIGN', (0,0), (0,0), 'LEFT'), ('VALIGN', (0,0), (0,0), 'TOP'),
            ('LEFTPADDING', (0,0), (0,0), 0), ('RIGHTPADDING', (0,0), (0,0), 0),
            ('TOPPADDING', (0,0), (0,0), 0), ('BOTTOMPADDING', (0,0), (0,0), 0),
        ]))
        fila_grid.append(contenedor_izq)

        if i + 1 < len(lista_celdas_etiquetas):
            txt_der, qr_der, w_t, w_q, h_t, w_c, pl_i, pr_i, pl_d, pr_d = lista_celdas_etiquetas[i+1]
            tarjeta_der = Table([[qr_der, txt_der]], colWidths=[w_q, w_t], rowHeights=[h_t])
            tarjeta_der.setStyle(TableStyle([
                ('VALIGN', (0,0), (-1,-1), 'MIDDLE'), ('ALIGN', (0,0), (0,0), 'CENTER'),
                ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#222222")),
                ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
                ('LEFTPADDING', (0,0), (-1,-1), pl_d), ('RIGHTPADDING', (0,0), (-1,-1), pr_d),
            ]))

            contenedor_der = Table([[tarjeta_der]], colWidths=[w_c])
            contenedor_der.setStyle(TableStyle([
                ('ALIGN', (0,0), (0,0), 'RIGHT'), ('VALIGN', (0,0), (0,0), 'TOP'),
                ('LEFTPADDING', (0,0), (0,0), 0), ('RIGHTPADDING', (0,0), (0,0), 0),
                ('TOPPADDING', (0,0), (0,0), 0), ('BOTTOMPADDING', (0,0), (0,0), 0),
            ]))
            fila_grid.append(contenedor_der)
        else:
            fila_grid.append("")
        grid_datos.append(fila_grid)

    ancho_col = 3.46 * inch if is_doble_carta else 2.7 * inch
    ancho_logo_box, alto_logo_box = (3.2 * inch, 0.6 * inch) if is_doble_carta else (2.8 * inch, 0.45 * inch)

    d_logo = Drawing(ancho_col, alto_logo_box)
    pos_x_logo = (ancho_col - ancho_logo_box) / 2.0
    if os.path.exists(logo_path):
        d_logo.add(RLImage(pos_x_logo, 0, ancho_logo_box, alto_logo_box, logo_path))
    else:
        d_logo = Paragraph("[ LOGO ]", ParagraphStyle('L', fontName='Helvetica-Bold', fontSize=14 if is_doble_carta else 12, textColor=colors.black, alignment=1))

    estilo_m = ParagraphStyle('M', fontName='Helvetica-Bold', fontSize=24 if is_doble_carta else 18, leading=26 if is_doble_carta else 20)
    estilo_d = ParagraphStyle('D', fontName='Helvetica', fontSize=13 if is_doble_carta else 10, leading=15 if is_doble_carta else 12, textColor=colors.HexColor("#333333"))

    p_mat = Paragraph(seleccion_material, estilo_m)
    p_def = Paragraph(def_material_txt, estilo_d) if def_material_txt else Paragraph("", estilo_d)

    comp_mat = Table([[p_mat], [p_def]], colWidths=[ancho_col])
    comp_mat.setStyle(TableStyle([
        ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
        ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE')
    ]))

    tabla_titulo = Table([[comp_mat, d_logo, maquina_global]], colWidths=[ancho_col, ancho_col, ancho_col], rowHeights=[0.75*inch if is_doble_carta else 0.55*inch])
    tabla_titulo.setStyle(TableStyle([
        ('FONTNAME', (2,0), (2,0), 'Helvetica-Bold'), ('FONTSIZE', (2,0), (2,0), 26 if is_doble_carta else 20),
        ('TEXTCOLOR', (0,0), (-1,-1), colors.black), ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#ffffff")),
        ('ALIGN', (0,0), (0,0), 'LEFT'), ('ALIGN', (1,0), (1,0), 'CENTER'), ('ALIGN', (2,0), (2,0), 'RIGHT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
        ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
    ]))
    story.append(tabla_titulo)
    story.append(Spacer(1, 15 if is_doble_carta else 10))

    total_filas = len(grid_datos) if len(grid_datos) > 0 else 1
    espacio_libre = (1100.0 - (total_filas * 72.0)) if is_doble_carta else (670.0 - (total_filas * 49.0))
    alto_sep = max(4.0 if is_doble_carta else 2.0, espacio_libre / max(1, (total_filas - 1)))
    w_fila = 5.1 * inch if is_doble_carta else 4.05 * inch

    for r_idx, fila_componentes in enumerate(grid_datos):
        tabla_fila = Table([fila_componentes], colWidths=[w_fila, w_fila])
        tabla_fila.setStyle(TableStyle([
            ('VALIGN', (0,0), (-1,-1), 'TOP'), ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
            ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
        ]))
        story.append(tabla_fila)
        if r_idx < total_filas - 1:
            story.append(Spacer(1, alto_sep))

    # --- HOJA 2: PACKAGING ---
    tiene_pallet = not df_pallet_filtrado.empty
    tiene_single = not df_single_filtrado.empty

    if tiene_pallet or tiene_single:
        story.append(PageBreak())

        d_logo_h2 = Drawing(ancho_col, alto_logo_box)
        if os.path.exists(logo_path):
            d_logo_h2.add(RLImage(pos_x_logo, 0, ancho_logo_box, alto_logo_box, logo_path))
        else:
            d_logo_h2 = Paragraph("[ LOGO ]", ParagraphStyle('L2', fontName='Helvetica-Bold', fontSize=14 if is_doble_carta else 12, textColor=colors.black, alignment=1))

        tabla_titulo_h2 = Table([["PALLET PACKAGING", d_logo_h2, "SINGLE PACKAGING"]], colWidths=[ancho_col, ancho_col, ancho_col], rowHeights=[0.6*inch if is_doble_carta else 0.45*inch])
        tabla_titulo_h2.setStyle(TableStyle([
            ('FONTNAME', (0,0), (-1,-1), 'Helvetica-Bold'), ('FONTSIZE', (0,0), (-1,-1), 22 if is_doble_carta else 16),
            ('TEXTCOLOR', (0,0), (-1,-1), colors.black), ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#ffffff")),
            ('ALIGN', (0,0), (0,0), 'LEFT'), ('ALIGN', (1,0), (1,0), 'CENTER'), ('ALIGN', (2,0), (2,0), 'RIGHT'),
            ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
            ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
            ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
        ]))
        story.append(tabla_titulo_h2)
        story.append(Spacer(1, 15 if is_doble_carta else 10))

        celdas_pallet = []
        if tiene_pallet:
            for idx_p, fila_p in df_pallet_filtrado.iterrows():
                p_mat_cod = str(fila_p[c_p_mat]).strip() if c_p_mat else ""
                if p_mat_cod == 'nan' or not p_mat_cod or p_mat_cod.isspace():
                    celdas_pallet.append("")
                    continue

                p_desc_txt = str(fila_p[c_p_desc]).strip() if c_p_desc else ""
                p_qty_val = str(fila_p[c_p_qty]).strip() if c_p_qty else ""
                if p_desc_txt == 'nan': p_desc_txt = ""
                if p_qty_val in ['nan', '0', '0.0']: p_qty_val = " "
                if p_qty_val.endswith('.0'): p_qty_val = p_qty_val[:-2]

                img_qr_p = generar_qr_empaque_pdf(p_mat_cod, p_qty_val, maquina_global)
                qr_p_file = os.path.join(temp_dir, f"qr_pallet_{c_idx}_{idx_p}.png")
                img_qr_p.save(qr_p_file)

                p_c = Paragraph(p_mat_cod, ParagraphStyle('PC', fontName='Helvetica-Bold', fontSize=16 if is_doble_carta else 12))
                p_d = Paragraph(p_desc_txt, estilo_desc)

                t_sub_p = Table([[p_c], [p_d]], colWidths=[2.3*inch if is_doble_carta else 1.6*inch], rowHeights=[0.45*inch if is_doble_carta else 0.34*inch, 0.45*inch if is_doble_carta else 0.34*inch])
                t_sub_p.setStyle(TableStyle([
                    ('ALIGN', (0,0), (-1,-1), 'LEFT'), ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
                    ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
                    ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
                ]))

                d_p = Drawing(ancho_qr_cont, alto_qr_cont)
                d_p.add(RLImage(pos_x, pos_y, tam_qr, tam_qr, qr_p_file))
                d_p.add(String(ancho_qr_cont / 2.0, 3 if is_doble_carta else 2, "1 PALLET", fontName='Helvetica-Bold', fontSize=font_pie, textAnchor='middle'))

                tarjeta_p = Table([[t_sub_p, d_p]], colWidths=[2.3*inch if is_doble_carta else 1.6*inch, ancho_qr_cont], rowHeights=[h_tarjeta])
                tarjeta_p.setStyle(TableStyle([
                    ('VALIGN', (0,0), (-1,-1), 'MIDDLE'), ('ALIGN', (1,0), (1,0), 'CENTER'),
                    ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#222222")),
                    ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
                    ('LEFTPADDING', (0,0), (-1,-1), pad_l_izq), ('RIGHTPADDING', (0,0), (-1,-1), pad_r_izq),
                ]))

                contenedor_p = Table([[tarjeta_p]], colWidths=[w_contenedor])
                contenedor_p.setStyle(TableStyle([
                    ('ALIGN', (0,0), (0,0), 'LEFT'), ('VALIGN', (0,0), (0,0), 'TOP'),
                    ('LEFTPADDING', (0,0), (0,0), 0), ('RIGHTPADDING', (0,0), (0,0), 0),
                    ('TOPPADDING', (0,0), (0,0), 0), ('BOTTOMPADDING', (0,0), (0,0), 0),
                ]))
                celdas_pallet.append(contenedor_p)

        celdas_single = []
        if tiene_single:
            for idx_s, fila_s in df_single_filtrado.iterrows():
                s_mat_cod = str(fila_s[c_s_mat]).strip() if c_s_mat else ""
                if s_mat_cod == 'nan' or not s_mat_cod or s_mat_cod.isspace():
                    celdas_single.append("")
                    continue

                s_desc_txt = str(fila_s[c_s_desc]).strip() if c_s_desc else ""
                s_qty_val = str(fila_s[c_s_qty]).strip() if c_s_qty else ""
                try:
                    num_cajas = int(float(s_qty_val))
                    texto_pie_qr_s = f"{num_cajas} CAJAS"
                except ValueError:
                    texto_pie_qr_s = f"{s_qty_val} CAJAS" if s_qty_val.strip() else "0 CAJAS"

                if s_desc_txt == 'nan': s_desc_txt = ""
                if s_qty_val in ['nan', '0', '0.0']: s_qty_val = " "
                if s_qty_val.endswith('.0'): s_qty_val = s_qty_val[:-2]

                img_qr_s = generar_qr_empaque_pdf(s_mat_cod, s_qty_val, maquina_global)
                qr_s_file = os.path.join(temp_dir, f"qr_single_{c_idx}_{idx_s}.png")
                img_qr_s.save(qr_s_file)

                s_c = Paragraph(s_mat_cod, ParagraphStyle('SC', fontName='Helvetica-Bold', fontSize=16 if is_doble_carta else 12))
                s_d = Paragraph(s_desc_txt, estilo_desc)

                t_sub_s = Table([[s_c], [s_d]], colWidths=[2.3*inch if is_doble_carta else 1.6*inch], rowHeights=[0.45*inch if is_doble_carta else 0.34*inch, 0.45*inch if is_doble_carta else 0.34*inch])
                t_sub_s.setStyle(TableStyle([
                    ('ALIGN', (0,0), (-1,-1), 'LEFT'), ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
                    ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
                    ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
                ]))

                d_s = Drawing(ancho_qr_cont, alto_qr_cont)
                d_s.add(RLImage(pos_x, pos_y, tam_qr, tam_qr, qr_s_file))
                d_s.add(String(ancho_qr_cont / 2.0, 3 if is_doble_carta else 2, texto_pie_qr_s, fontName='Helvetica-Bold', fontSize=font_pie, textAnchor='middle'))

                tarjeta_s = Table([[d_s, t_sub_s]], colWidths=[ancho_qr_cont, 2.3*inch if is_doble_carta else 1.6*inch], rowHeights=[h_tarjeta])
                tarjeta_s.setStyle(TableStyle([
                    ('VALIGN', (0,0), (-1,-1), 'MIDDLE'), ('ALIGN', (0,0), (0,0), 'CENTER'),
                    ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#222222")),
                    ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
                    ('LEFTPADDING', (0,0), (-1,-1), pad_l_der), ('RIGHTPADDING', (0,0), (-1,-1), pad_r_der),
                ]))

                contenedor_s = Table([[tarjeta_s]], colWidths=[w_contenedor])
                contenedor_s.setStyle(TableStyle([
                    ('ALIGN', (0,0), (0,0), 'RIGHT'), ('VALIGN', (0,0), (0,0), 'TOP'),
                    ('LEFTPADDING', (0,0), (0,0), 0), ('RIGHTPADDING', (0,0), (0,0), 0),
                    ('TOPPADDING', (0,0), (0,0), 0), ('BOTTOMPADDING', (0,0), (0,0), 0),
                ]))
                celdas_single.append(contenedor_s)

        grid_empaques = []
        max_filas_h2 = max(len(celdas_pallet), len(celdas_single))
        for f_idx in range(max_filas_h2):
            c_pallet_actual = celdas_pallet[f_idx] if f_idx < len(celdas_pallet) else ""
            c_single_actual = celdas_single[f_idx] if f_idx < len(celdas_single) else ""
            grid_empaques.append([c_pallet_actual, c_single_actual])

        total_filas_h2 = len(grid_empaques) if len(grid_empaques) > 0 else 1
        espacio_libre_h2 = (1050.0 - (total_filas_h2 * 72.0)) if is_doble_carta else (680.0 - (total_filas_h2 * 49.0))
        alto_sep_h2 = max(4.0 if is_doble_carta else 2.0, espacio_libre_h2 / max(1, (total_filas_h2 - 1)))

        for r_idx_h2, fila_empaques in enumerate(grid_empaques):
            tabla_fila_h2 = Table([fila_empaques], colWidths=[w_fila, w_fila])
            tabla_fila_h2.setStyle(TableStyle([
                ('VALIGN', (0,0), (-1,-1), 'TOP'), ('LEFTPADDING', (0,0), (-1,-1), 0), ('RIGHTPADDING', (0,0), (-1,-1), 0),
                ('TOPPADDING', (0,0), (-1,-1), 0), ('BOTTOMPADDING', (0,0), (-1,-1), 0),
            ]))
            story.append(tabla_fila_h2)
            if r_idx_h2 < total_filas_h2 - 1:
                story.append(Spacer(1, alto_sep_h2))

    doc.build(story)
    return pdf_salida

# ==============================================================================
# INTERFAZ KIVY (ANDROID MULTIPLATAFORMA)
# ==============================================================================
class AppEscaneoKivy(App):
    def build(self):
        self.title = "Visor de Componentes"
        self.df = None
        self.df_pallet_base = pd.DataFrame()
        self.df_single_base = pd.DataFrame()
        self.cols_info = None
        self.lista_materiales_unicos = []
        self.material_seleccionado_actual = None
        self.maquina_seleccionada_actual = None

        # Contenedor Principal Vertical
        root = BoxLayout(orientation='vertical', padding=10, spacing=10)

        # 1. BARRA SUPERIOR
        top_bar = BoxLayout(orientation='horizontal', size_hint_y=None, height='45dp', spacing=10)
        
        btn_sync = Button(text="🔄 Actualizar", size_hint_x=0.4)
        btn_sync.bind(on_press=lambda x: threading.Thread(target=self.cargar_excel_desde_internet, daemon=True).start())
        top_bar.add_widget(btn_sync)

        btn_pdf = Button(text="🖨️ PDF", size_hint_x=0.3, background_color=(0.18, 0.49, 0.2, 1))
        btn_pdf.bind(on_press=self.abrir_dialogo_pdf)
        top_bar.add_widget(btn_pdf)

        self.lbl_estado = Label(text="Iniciando...", size_hint_x=0.3, font_size='12sp', color=(1, 0.6, 0, 1))
        top_bar.add_widget(self.lbl_estado)

        root.add_widget(top_bar)

        # 2. CAMPO BUSCADOR
        self.txt_buscar = TextInput(hint_text="🔍 Buscar Material...", multiline=False, size_hint_y=None, height='40dp')
        self.txt_buscar.bind(text=self.al_escribir_buscador)
        root.add_widget(self.txt_buscar)

        # 3. LISTA DESPLEGABLE DE SUGERENCIAS
        self.scroll_sugerencias = ScrollView(size_hint_y=None, height='120dp')
        self.box_sugerencias = GridLayout(cols=1, spacing=2, size_hint_y=None)
        self.box_sugerencias.bind(minimum_height=self.box_sugerencias.setter('height'))
        self.scroll_sugerencias.add_widget(self.box_sugerencias)
        root.add_widget(self.scroll_sugerencias)

        # 4. HEADER DETALLE DE MATERIAL
        self.box_info = BoxLayout(orientation='vertical', size_hint_y=None, height='60dp')
        self.lbl_def = Label(text="Seleccione un material...", font_size='14sp', bold=True)
        self.lbl_meta = Label(text="Máquina: --- | Material: ---", font_size='12sp', color=(0.7, 0.7, 0.7, 1))
        self.box_info.add_widget(self.lbl_def)
        self.box_info.add_widget(self.lbl_meta)
        root.add_widget(self.box_info)

        # 5. CONTENEDOR PRINCIPAL TARJETAS (SCROLL)
        self.scroll_main = ScrollView(size_hint=(1, 1))
        self.grid_tarjetas = GridLayout(cols=1, spacing=15, size_hint_y=None)
        self.grid_tarjetas.bind(minimum_height=self.grid_tarjetas.setter('height'))
        self.scroll_main.add_widget(self.grid_tarjetas)
        root.add_widget(self.scroll_main)

        descargar_logo(URL_LOGO, "logo.png")
        threading.Thread(target=self.cargar_excel_desde_internet, daemon=True).start()

        return root

    def mostrar_alerta(self, titulo, mensaje):
        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        content.add_widget(Label(text=mensaje))
        btn = Button(text="OK", size_hint_y=None, height='40dp')
        content.add_widget(btn)
        popup = Popup(title=titulo, content=content, size_hint=(0.8, 0.4))
        btn.bind(on_press=popup.dismiss)
        popup.open()

    def cargar_excel_desde_internet(self):
        self.lbl_estado.text = "Cargando..."
        try:
            try:
                res = requests.get(URL_EXCEL, timeout=12, verify=certifi.where())
            except Exception:
                res = requests.get(URL_EXCEL, timeout=12, verify=False)
            res.raise_for_status()

            excel_bytes = io.BytesIO(res.content)
            self.df = pd.read_excel(excel_bytes, sheet_name=0)

            col_x_real = next((str(c).strip() for c in self.df.columns if str(c).strip() == "X?"), None)
            self.df.columns = self.df.columns.astype(str).str.strip().str.lower()

            col_maquina = next((c for c in self.df.columns if 'maqu' in c or 'mág' in c), None)
            col_material = next((c for c in self.df.columns if 'mat' in c), None)
            col_componente = next((c for c in self.df.columns if 'comp' in c or 'cod' in c or 'cód' in c), None)
            col_descripcion = next((c for c in self.df.columns if 'desc' in c), None)
            col_definicion = next((c for c in self.df.columns if c == 'def' or 'defin' in c), None)
            col_cantidad = next((c for c in self.df.columns if 'cant' in c or 'qty' in c), None)
            col_x = col_x_real.lower() if col_x_real else None

            self.df[col_material] = self.df[col_material].apply(limpiar_texto)
            self.df[col_maquina] = self.df[col_maquina].apply(limpiar_texto)

            try:
                self.df_pallet_base = pd.read_excel(excel_bytes, sheet_name=1)
                self.df_pallet_base.columns = self.df_pallet_base.columns.astype(str).str.strip().str.lower()
                c_p_mat = next((c for c in self.df_pallet_base.columns if 'pack' in c), None)
                c_p_desc = next((c for c in self.df_pallet_base.columns if 'desc' in c), None)
                c_p_qty = next((c for c in self.df_pallet_base.columns if 'qty' in c or 'quantity' in c), None)
                c_p_ref_mat = next((c for c in self.df_pallet_base.columns if 'material' in c and c != c_p_mat), None)
            except Exception:
                self.df_pallet_base = pd.DataFrame()
                c_p_mat = c_p_desc = c_p_qty = c_p_ref_mat = None

            try:
                self.df_single_base = pd.read_excel(excel_bytes, sheet_name=2)
                self.df_single_base.columns = self.df_single_base.columns.astype(str).str.strip().str.lower()
                c_s_mat = next((c for c in self.df_single_base.columns if 'pack' in c), None)
                c_s_desc = next((c for c in self.df_single_base.columns if 'desc' in c), None)
                c_s_qty = next((c for c in self.df_single_base.columns if 'qty' in c or 'quantity' in c), None)
                c_s_ref_mat = next((c for c in self.df_single_base.columns if 'material' in c and c != c_s_mat), None)
            except Exception:
                self.df_single_base = pd.DataFrame()
                c_s_mat = c_s_desc = c_s_qty = c_s_ref_mat = None

            self.cols_info = (col_material, col_maquina, col_componente, col_descripcion, col_definicion, col_cantidad, col_x,
                              c_p_mat, c_p_desc, c_p_qty, c_p_ref_mat, c_s_mat, c_s_desc, c_s_qty, c_s_ref_mat)

            self.lista_materiales_unicos.clear()
            cols_sel = [col_material, col_maquina]
            if col_definicion: cols_sel.append(col_definicion)

            df_unicos = self.df[cols_sel].drop_duplicates()
            for _, fila in df_unicos.iterrows():
                mat = limpiar_texto(fila[col_material])
                maq = limpiar_texto(fila[col_maquina])
                desc = limpiar_texto(fila[col_definicion]) if col_definicion else ""
                if mat: self.lista_materiales_unicos.append((mat, maq, desc))

            self.lbl_estado.text = "● En Línea"
            if self.lista_materiales_unicos:
                pm, pq, _ = self.lista_materiales_unicos[0]
                self.seleccionar_material(pm, pq)

        except Exception as e:
            self.lbl_estado.text = "Error Red"

    def al_escribir_buscador(self, instance, text):
        query = normalizar_texto(text)
        self.box_sugerencias.clear_widgets()
        if not query or not self.lista_materiales_unicos: return

        count = 0
        for mat, maq, desc in self.lista_materiales_unicos:
            if query in normalizar_texto(f"{mat} {maq} {desc}"):
                btn = Button(text=f"{mat} - Máq: {maq}", size_hint_y=None, height='35dp')
                btn.bind(on_press=lambda x, m=mat, mq=maq: self.seleccionar_material(m, mq))
                self.box_sugerencias.add_widget(btn)
                count += 1
                if count >= 10: break

    def seleccionar_material(self, mat, maq):
        self.box_sugerencias.clear_widgets()
        self.material_seleccionado_actual = mat
        self.maquina_seleccionada_actual = maq
        self.desplegar_tarjetas(mat, maq)

    def desplegar_tarjetas(self, mat, maq):
        self.grid_tarjetas.clear_widgets()
        if self.df is None: return

        col_material, col_maquina, col_componente, col_descripcion, col_definicion, col_cantidad, col_x = self.cols_info[:7]
        df_filtrado = self.df[(self.df[col_material] == mat) & (self.df[col_maquina] == maq)]

        def_txt = limpiar_texto(df_filtrado.iloc[0][col_definicion]) if col_definicion and not df_filtrado.empty else ""
        self.lbl_def.text = def_txt if def_txt else mat
        self.lbl_meta.text = f"Máquina: {maq} | Material: {mat}"

        for _, fila in df_filtrado.iterrows():
            comp = limpiar_texto(fila[col_componente])
            desc = limpiar_texto(fila[col_descripcion]) if col_descripcion else ""
            qty = limpiar_entero(fila[col_cantidad]) if col_cantidad else ""
            val_x = limpiar_texto(fila[col_x]).lower() if col_x else ""
            datos_qr = f"5X{comp}/{qty}/{maq}/930" if val_x == "x" else f"/{comp}/{qty}/{maq}"
            
            card = self.crear_tarjeta_widget(comp, desc, obtener_texto_cantidad_etiqueta(qty), datos_qr)
            self.grid_tarjetas.add_widget(card)

    def crear_tarjeta_widget(self, comp, desc, pie_qr, datos_qr):
        card = BoxLayout(orientation='horizontal', padding=8, spacing=8, size_hint_y=None, height='110dp')
        
        box_txt = BoxLayout(orientation='vertical', size_hint_x=0.65)
        box_txt.add_widget(Label(text=comp, font_size='16sp', bold=True, halign='left', valign='middle'))
        box_txt.add_widget(Label(text=desc, font_size='12sp', color=(0.8, 0.8, 0.8, 1), halign='left', valign='middle'))
        card.add_widget(box_txt)

        if datos_qr:
            qr = qrcode.QRCode(version=1, box_size=3, border=1)
            qr.add_data(datos_qr)
            qr.make(fit=True)
            img_pil = qr.make_image(fill_color="black", back_color="white")
            
            box_qr = BoxLayout(orientation='vertical', size_hint_x=0.35)
            img_widget = Image(texture=pil_to_kivy_texture(img_pil))
            box_qr.add_widget(img_widget)
            box_qr.add_widget(Label(text=pie_qr, font_size='10sp', size_hint_y=0.25))
            card.add_widget(box_qr)

        return card

    def abrir_dialogo_pdf(self, instance):
        if not self.material_seleccionado_actual:
            self.mostrar_alerta("Atención", "Seleccione un material primero.")
            return

        content = BoxLayout(orientation='vertical', padding=10, spacing=10)
        content.add_widget(Label(text="Seleccione formato PDF:"))
        
        btn_carta = Button(text="Carta (8.5 x 11 in)", size_hint_y=None, height='40dp')
        btn_doble = Button(text="Doble Carta (11 x 17 in)", size_hint_y=None, height='40dp')
        
        content.add_widget(btn_carta)
        content.add_widget(btn_doble)

        popup = Popup(title="Exportar PDF", content=content, size_hint=(0.8, 0.5))
        
        btn_carta.bind(on_press=lambda x: self.generar_pdf_android(popup, "Carta"))
        btn_doble.bind(on_press=lambda x: self.generar_pdf_android(popup, "Doble Carta"))
        popup.open()

    def generar_pdf_android(self, popup, tamano):
        popup.dismiss()
        self.lbl_estado.text = "Generando..."
        
        def tarea():
            carpeta_dest = obtener_carpeta_descargas_android()
            try:
                res_path = construir_pdf_por_tamano(
                    self.df, self.df_pallet_base, self.df_single_base,
                    self.material_seleccionado_actual, self.maquina_seleccionada_actual,
                    "logo.png", carpeta_dest, 1, self.cols_info, tamano_hoja=tamano
                )
                self.lbl_estado.text = "● En Línea"
                self.mostrar_alerta("PDF Creado", f"Guardado en:\n{res_path}")
            except Exception as e:
                self.lbl_estado.text = "● En Línea"
                self.mostrar_alerta("Error PDF", str(e))

        threading.Thread(target=tarea, daemon=True).start()

if __name__ == '__main__':
    AppEscaneoKivy().run()
