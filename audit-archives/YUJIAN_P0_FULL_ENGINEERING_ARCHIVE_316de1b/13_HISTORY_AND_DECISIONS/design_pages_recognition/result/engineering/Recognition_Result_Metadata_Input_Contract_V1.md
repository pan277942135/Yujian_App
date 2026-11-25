# Recognition Result Metadata Input Contract V1.1

Status: **FROZEN**

Machine-readable authority: `metadata_input_contract.json`.

Metadata is optional and remains available on High, Medium after explicit
selection, and Low before or after species selection. Low metadata is local
draft state until a species is selected and a real save is requested.

## Result presentation

Use one shared Glass surface with three vertical rows:

1. `[icon] 长度` — blank: `请输入`, example: `42.6 cm`;
2. `[icon] 重量` — blank: `请输入`, example: `1.28 kg`;
3. `[icon] 地点` — blank: `请选择`, example: `千岛湖`.

Rows are full-width touch targets with restrained separators. There is no
horizontal three-column strip and no generic settings row.

## Length

- title: `鱼获长度`;
- decimal IME opens automatically;
- empty is allowed;
- valid nonempty range: 0.1–999.9 cm;
- maximum precision: 1 decimal place;
- invalid input stays in the sheet with `请输入 0.1–999.9 cm 的长度`;
- clear is supported;
- valid commit returns to the same Result page.

## Weight

- title: `鱼获重量`;
- decimal IME opens automatically;
- empty is allowed;
- valid nonempty range: 0.01–999.99 kg;
- maximum precision: 2 decimal places;
- invalid input stays in the sheet with `请输入 0.01–999.99 kg 的重量`;
- clear is supported;
- valid commit returns to the same Result page.

## Location

- title: `鱼获地点`;
- same-page Bottom Sheet;
- search does not commit until a place is selected;
- current location is requested only after explicit user action;
- recent locations are local and capped at three;
- selecting a place commits immediately;
- no new page navigation.

## Story relationship

The Story Card is independent of species resolution. It uses title
`写下这次鱼获的故事`, placeholder `记录这一刻的感受……`, multiline input, a
300 Unicode code-point limit, and a live `{count}/300` counter.
