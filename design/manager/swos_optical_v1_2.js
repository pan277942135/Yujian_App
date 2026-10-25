(function () {
  "use strict";
  // Reads the ONE V1.2 machine source. Do not replicate or promote candidate numbers in JS.
  function node(tag, className, value) {
    const item = document.createElement(tag);
    if (className) item.className = className;
    if (value != null) item.textContent = String(value);
    return item;
  }
  function card(title, kind) {
    const el = node("section", "swos-v12-card");
    const heading = node("div", "swos-v12-card-head");
    heading.append(node("h4", "", title), node("span", "swos-v12-kind", kind));
    el.append(heading);
    return el;
  }
  function table(headers, rows) {
    const outer = node("div", "swos-v12-scroll");
    const tab = node("table", "swos-v12-table");
    const head = node("thead");
    const tr = node("tr");
    headers.forEach(h => tr.append(node("th", "", h)));
    head.append(tr);
    tab.append(head);
    const body = node("tbody");
    rows.forEach(row => {
      const tr = node("tr");
      row.forEach(cell => tr.append(node("td", "", cell)));
      body.append(tr);
    });
    tab.append(body);
    outer.append(tab);
    return outer;
  }
  function range(x, suffix) {
    if (Array.isArray(x)) return x.join("–") + (suffix || "");
    return x == null ? "—" : String(x) + (suffix || "");
  }
  function render(panel, config, view) {
    if (config.contract_id !== "YUJIAN-SWOS-RR09-OPTICAL-RENDERING-ACCEPTANCE-V1_2")
      throw new Error("Optical contract identity mismatch");
    const status = node("div", "swos-v12-status");
    status.append(node("strong", "", "V1.2 · 实施合同已冻结"),
      node("p", "", "新增数值仍是候选标定；真实照片、Mask、连续动效及浏览器性能尚未验收。"),
      node("span", "", config.numeric_calibration_status + " / " + config.runtime_visual_status));
    panel.append(status);

    const frozen = card("既有冻结曲线 · 仅相对强度", "FROZEN_EXISTING");
    frozen.append(table(["帧","时间 ms","水光强度","鱼体金光"],
      config.frozen.keyframes.map(f => [f.id,f.global_ms,f.ambient_water_strength,f.fish_gold_strength])));
    frozen.append(node("p","swos-v12-footnote","0–100 是相对视觉强度，不是 CSS opacity。P4–P7 鱼体保持 100，P7 外围归零。"));
    panel.append(frozen);

    const palette = card("光学色板 · 首轮候选", "CALIBRATION_CANDIDATE");
    const colors=node("div","swos-v12-colors");
    Object.entries(config.calibration_candidate.palette).forEach(([key,hex])=>{
      const item=node("div","swos-v12-swatch");
      const chip=node("span","swos-v12-chip");chip.style.background=hex;
      item.append(chip,node("b","",key.replaceAll("_"," ")),node("code","",hex));
      colors.append(item);
    });
    palette.append(colors);
    panel.append(palette);

    const p=config.calibration_candidate;
    const geometry=card("水光 / 鱼体几何的候选范围", "CALIBRATION_CANDIDATE");
    geometry.append(table(["参数","标定范围","单位/口径"],[
      ["传播速度",range(p.caustics.mean_front_speed_H_per_s),"H / s"],
      ["焦散斑块数",range(p.caustics.patch_count_for_reference_photo),"参考手机照片"],
      ["焦散宽度",range(p.caustics.patch_width_W),"× W"],
      ["局部 Alpha",range(p.caustics.local_peak_alpha),"仅局部斑块"],
      ["P3 覆盖率",range(p.caustics.coverage_percent_at_keyframe.P3,"%"),"可见照片占比"],
      ["水纹折射",range(p.ripple_refraction.displacement_css_px),"CSS px"],
      ["早期聚焦范围",range(p.fish_focus.early_effect_extent_outside_contour_L),"× 鱼体长度 L"],
      ["P4 Halo 范围",range(p.fish_focus.peak_halo_extent_outside_contour_L),"× L"],
      ["Core 宽度",range(p.fish_focus.fish_core.contour_width_css_px),"CSS px"],
      ["Soft Blur",range(p.fish_focus.fish_soft_glow.blur_css_px),"CSS px"],
      ["Halo Blur",range(p.fish_focus.fish_halo.blur_css_px),"CSS px"]
    ]));
    panel.append(geometry);

    const handoff=card("RR09 与实际验收", "NOT_VERIFIED");
    handoff.append(table(["采样","全局时间","动作"],config.frozen.reveal.map(f=>[
      f.id,f.global_ms, f.geometry === "MID_FLIGHT" ? "同一照片真实几何中点" :
      f.id==="R2" ? "金光转译主要完成" :
      f.id==="R4" ? "中文名称完全可读" :
      f.id==="R5" ? "余辉为零" : f.geometry
    ])));
    handoff.append(node("p","swos-v12-footnote","必须实测 P3、P4、P7、R0.5、R2、R4，再覆盖完整 15 帧和九视口；CI 成功不等于视觉 PASS。"));
    panel.append(handoff);

    const footer=node("p","swos-v12-footnote","源合同：" + view.machine_authority + " · 静态查看只呈现机器参数，不运行模型、不上传照片、不改变候选状态。");
    panel.append(footer);
  }
  window.SWOSOpticalV12 = {
    mount: async function(root, view) {
      const panel = root.querySelector("[data-swos-v12-panel]");
      if (!panel) return;
      const path = view.machine_authority;
      if (!path) { panel.textContent = "缺少参数权威路径";return; }
      try {
        const response = await fetch("../../" + path, {cache:"no-store"});
        if (!response.ok) throw new Error("HTTP " + response.status);
        const config = await response.json();
        if (!panel.isConnected) return;
        panel.replaceChildren();
        render(panel,config,view);
      } catch(err) {
        if (panel.isConnected) panel.textContent="机器参数加载失败（不得使用硬编码候选值代替）：" + String(err.message||err);
      }
    }
  };
})();
