-- Pandoc filter for the Word reports (build-report.sh): column widths in proportion to their
-- text, never narrower than the header or the longest word, with fixed widths for the tables
-- whose short columns would otherwise wrap.
local MIN_SHARE = 8
local function shares(rows, ncols)
  local total, count, word = {}, {}, {}
  for i = 1, ncols do total[i], count[i], word[i] = 0, 0, 0 end
  for _, row in ipairs(rows) do
    for i = 1, ncols do
      if row[i] then
        local text = pandoc.utils.stringify(row[i])
        total[i] = total[i] + #text; count[i] = count[i] + 1
        for w in text:gmatch("%S+") do if #w < 25 then word[i] = math.max(word[i], #w) end end
      end
    end
  end
  local widths, sum = {}, 0
  for i = 1, ncols do
    local header = rows[1] and rows[1][i] and #pandoc.utils.stringify(rows[1][i]) + 2 or 0
    widths[i] = math.max(MIN_SHARE, header, word[i] * 1.4 + 2, count[i] > 0 and total[i] / count[i] or MIN_SHARE)
    sum = sum + widths[i]
  end
  for i = 1, ncols do widths[i] = widths[i] / sum end
  return widths
end
function Table(t)
  local rows = {}
  local function add(row)
    local cells = {}
    for _, cell in ipairs(row.cells) do cells[#cells + 1] = cell.contents end
    rows[#rows + 1] = cells
  end
  for _, row in ipairs(t.head.rows) do add(row) end
  for _, body in ipairs(t.bodies) do for _, row in ipairs(body.body) do add(row) end end
  local widths = shares(rows, #t.colspecs)
  -- The compliance table: fixed widths, so "Level" and "Decided (I4)" stay on one line.
  local first = pandoc.utils.stringify(rows[1][1])
  local fixed = {
    ["ID"] = { 0.07, 0.23, 0.08, 0.14, 0.48 },
    ["Tool or agent (and model)"] = { 0.20, 0.12, 0.38, 0.30 },
    ["Integration"] = { 0.14, 0.20, 0.36, 0.30 },
    ["Step"] = { 0.07, 0.13, 0.27, 0.25, 0.28 },
  }
  if fixed[first] and #fixed[first] == #t.colspecs then widths = fixed[first] end
  for i, spec in ipairs(t.colspecs) do t.colspecs[i] = { spec[1], widths[i] } end
  return t
end
